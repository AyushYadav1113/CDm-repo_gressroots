package com.grassroots.cdm.matching;

import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.matching.config.MatchingProperties;
import com.grassroots.cdm.matching.evaluator.impl.CommonNameMatchEvaluator;
import com.grassroots.cdm.matching.evaluator.impl.IssuerContinuityEvaluator;
import com.grassroots.cdm.matching.evaluator.impl.LifecycleEvaluator;
import com.grassroots.cdm.matching.evaluator.impl.RenewalLinkageEvaluator;
import com.grassroots.cdm.matching.evaluator.impl.SanMatchEvaluator;
import com.grassroots.cdm.matching.generator.impl.DefaultCandidateGenerator;
import com.grassroots.cdm.matching.impl.ScoringCertificateMatcher;
import com.grassroots.cdm.matching.model.CandidateMatchResult;
import com.grassroots.cdm.matching.model.MatchDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateMatcherUnitTest {

    private ScoringCertificateMatcher matcher;
    private MatchingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MatchingProperties();
        properties.setAutoMatchThreshold(0.85);
        properties.setReviewThreshold(0.60);
        properties.setAmbiguityMargin(0.05);
        properties.setSanWeight(0.35);
        properties.setCnWeight(0.25);
        properties.setRenewalLinkageWeight(0.20);
        properties.setLifecycleWeight(0.10);
        properties.setIssuerWeight(0.10);
        properties.setRejectExpiredCandidates(true);
        properties.setAllowWildcardExpansion(true);

        DefaultCandidateGenerator candidateGenerator = new DefaultCandidateGenerator(properties);

        matcher = new ScoringCertificateMatcher(
                properties,
                candidateGenerator,
                List.of(
                        new SanMatchEvaluator(properties),
                        new CommonNameMatchEvaluator(properties),
                        new RenewalLinkageEvaluator(properties),
                        new LifecycleEvaluator(properties),
                        new IssuerContinuityEvaluator(properties)
                )
        );
    }

    @Test
    @DisplayName("Scenario 1: Exact match on CN, SANs, CA Issuer, and renewal linkage")
    void scenario01_exactMatch() {
        CertificateRecord oldCert = createCert("api.example.com", "api.example.com, portal.example.com",
                "ORD-100", "CN=Sectigo RSA CA", Instant.now().minus(300, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS));

        CertificateRecord newCert = createCert("api.example.com", "api.example.com, portal.example.com",
                "ORD-100", "CN=Sectigo RSA CA", Instant.now(), Instant.now().plus(730, ChronoUnit.DAYS));

        CandidateMatchResult result = matcher.match(oldCert, List.of(newCert));

        assertThat(result.decision()).isEqualTo(MatchDecision.AUTOMATIC_MATCH);
        assertThat(result.matchScore()).isEqualTo(1.0);
        assertThat(result.matchedCandidate()).contains(newCert);
        assertThat(result.decisionSummary()).contains("High confidence match");
    }

    @Test
    @DisplayName("Scenario 2: SAN match when CN differs or is superset")
    void scenario02_sanMatch() {
        CertificateRecord oldCert = createCert("server01.internal", "payment.example.com, checkout.example.com",
                "ORD-OLD", "CN=Sectigo CA", Instant.now().minus(300, ChronoUnit.DAYS), Instant.now().plus(10, ChronoUnit.DAYS));

        CertificateRecord newCert = createCert("payment.example.com", "payment.example.com, checkout.example.com, api.example.com",
                "ORD-NEW", "CN=Sectigo CA", Instant.now(), Instant.now().plus(730, ChronoUnit.DAYS));

        CandidateMatchResult result = matcher.match(oldCert, List.of(newCert));

        assertThat(result.matchedCandidate()).contains(newCert);
        // SAN evaluator gives 0.90 (superset) -> weighted 0.315 + CN in SAN gives 0.70 -> weighted 0.175 + CA 0.10 + lifecycle 0.10 = 0.69
        assertThat(result.matchScore()).isGreaterThanOrEqualTo(properties.getReviewThreshold());
        assertThat(result.decision()).isIn(MatchDecision.AUTOMATIC_MATCH, MatchDecision.REVIEW_REQUIRED);
    }

    @Test
    @DisplayName("Scenario 3: Common Name match when SANs are absent")
    void scenario03_commonNameMatch() {
        CertificateRecord oldCert = createCert("legacy.example.com", null,
                "ORD-LEGACY", "CN=Sectigo CA", Instant.now().minus(300, ChronoUnit.DAYS), Instant.now().plus(15, ChronoUnit.DAYS));

        CertificateRecord newCert = createCert("legacy.example.com", null,
                "ORD-LEGACY", "CN=Sectigo CA", Instant.now(), Instant.now().plus(730, ChronoUnit.DAYS));

        CandidateMatchResult result = matcher.match(oldCert, List.of(newCert));

        assertThat(result.matchedCandidate()).contains(newCert);
        assertThat(result.decision()).isEqualTo(MatchDecision.AUTOMATIC_MATCH);
        assertThat(result.matchScore()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Scenario 4: Different SANs results in non-match")
    void scenario04_differentSan() {
        CertificateRecord oldCert = createCert("app.example.com", "app.example.com",
                "ORD-A", "CN=Sectigo CA", Instant.now().minus(300, ChronoUnit.DAYS), Instant.now().plus(20, ChronoUnit.DAYS));

        CertificateRecord newCert = createCert("billing.example.com", "billing.example.com, payments.example.com",
                "ORD-B", "CN=DigiCert CA", Instant.now(), Instant.now().plus(730, ChronoUnit.DAYS));

        CandidateMatchResult result = matcher.match(oldCert, List.of(newCert));

        assertThat(result.decision()).isEqualTo(MatchDecision.NO_MATCH);
        assertThat(result.matchedCandidate()).isEmpty();
    }

    @Test
    @DisplayName("Scenario 5: Wildcard certificates match single-level subdomains")
    void scenario05_wildcardCertificates() {
        CertificateRecord oldCert = createCert("api.example.com", "api.example.com",
                "ORD-API", "CN=Sectigo CA", Instant.now().minus(200, ChronoUnit.DAYS), Instant.now().plus(20, ChronoUnit.DAYS));

        CertificateRecord newWildcardCert = createCert("*.example.com", "*.example.com",
                "ORD-WILD", "CN=Sectigo CA", Instant.now(), Instant.now().plus(730, ChronoUnit.DAYS));

        CandidateMatchResult result = matcher.match(oldCert, List.of(newWildcardCert));

        assertThat(result.matchedCandidate()).contains(newWildcardCert);
        assertThat(result.matchScore()).isGreaterThanOrEqualTo(properties.getReviewThreshold());
        assertThat(result.decision()).isIn(MatchDecision.AUTOMATIC_MATCH, MatchDecision.REVIEW_REQUIRED);

        // Negative check: Multi-level subdomains should NOT match wildcard
        CertificateRecord deepSubdomain = createCert("deep.sub.api.example.com", "deep.sub.api.example.com",
                "ORD-DEEP", "CN=Sectigo CA", Instant.now().minus(200, ChronoUnit.DAYS), Instant.now().plus(20, ChronoUnit.DAYS));

        CandidateMatchResult deepResult = matcher.match(deepSubdomain, List.of(newWildcardCert));
        assertThat(deepResult.decision()).isEqualTo(MatchDecision.NO_MATCH);
    }

    @Test
    @DisplayName("Scenario 6: Multiple candidates ranked deterministically by score")
    void scenario06_multipleCandidates() {
        CertificateRecord oldCert = createCert("portal.example.com", "portal.example.com",
                "ORD-P", "CN=Sectigo CA", Instant.now().minus(200, ChronoUnit.DAYS), Instant.now().plus(10, ChronoUnit.DAYS));

        CertificateRecord wrongCert = createCert("unrelated.org", "unrelated.org",
                "ORD-U", "CN=GlobalSign", Instant.now(), Instant.now().plus(730, ChronoUnit.DAYS));

        CertificateRecord partialCert = createCert("portal.example.com", "portal.example.com",
                "ORD-DIFF", "CN=Let's Encrypt", Instant.now(), Instant.now().plus(730, ChronoUnit.DAYS));

        CertificateRecord bestCert = createCert("portal.example.com", "portal.example.com",
                "ORD-P", "CN=Sectigo CA", Instant.now(), Instant.now().plus(730, ChronoUnit.DAYS));

        CandidateMatchResult result = matcher.match(oldCert, List.of(wrongCert, partialCert, bestCert));

        assertThat(result.decision()).isEqualTo(MatchDecision.AUTOMATIC_MATCH);
        assertThat(result.matchedCandidate()).contains(bestCert);
        assertThat(result.rankedCandidates().get(0).candidate()).isEqualTo(bestCert);
    }

    @Test
    @DisplayName("Scenario 7: No candidates returns NO_MATCH")
    void scenario07_noCandidates() {
        CertificateRecord oldCert = createCert("isolated.example.com", "isolated.example.com",
                "ORD-ISO", "CN=Sectigo CA", Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));

        CandidateMatchResult result = matcher.match(oldCert, List.of());

        assertThat(result.decision()).isEqualTo(MatchDecision.NO_MATCH);
        assertThat(result.matchedCandidate()).isEmpty();
        assertThat(result.decisionSummary()).contains("No eligible replacement candidates");
    }

    @Test
    @DisplayName("Scenario 8: Ambiguous candidates downgraded to REVIEW_REQUIRED")
    void scenario08_ambiguousCandidates() {
        CertificateRecord oldCert = createCert("auth.example.com", "auth.example.com",
                "ORD-X", "CN=Sectigo CA", Instant.now().minus(200, ChronoUnit.DAYS), Instant.now().plus(10, ChronoUnit.DAYS));

        // Two competing candidates with identical or near-identical high scores
        CertificateRecord cand1 = createCert("auth.example.com", "auth.example.com",
                "ORD-1", "CN=Sectigo CA", Instant.now(), Instant.now().plus(730, ChronoUnit.DAYS));

        CertificateRecord cand2 = createCert("auth.example.com", "auth.example.com",
                "ORD-2", "CN=Sectigo CA", Instant.now(), Instant.now().plus(700, ChronoUnit.DAYS));

        CandidateMatchResult result = matcher.match(oldCert, List.of(cand1, cand2));

        // Must NOT auto-match ambiguous candidates!
        assertThat(result.decision()).isEqualTo(MatchDecision.REVIEW_REQUIRED);
        assertThat(result.decisionSummary()).contains("Ambiguous match");
        assertThat(result.matchedCandidate()).isPresent();
    }

    @Test
    @DisplayName("Scenario 9: Expired candidate certificates are disqualified")
    void scenario09_expiredCertificates() {
        CertificateRecord oldCert = createCert("prod.example.com", "prod.example.com",
                "ORD-PROD", "CN=Sectigo CA", Instant.now().minus(400, ChronoUnit.DAYS), Instant.now().minus(10, ChronoUnit.DAYS));

        CertificateRecord expiredCandidate = createCert("prod.example.com", "prod.example.com",
                "ORD-PROD", "CN=Sectigo CA", Instant.now().minus(800, ChronoUnit.DAYS), Instant.now().minus(100, ChronoUnit.DAYS));

        CandidateMatchResult result = matcher.match(oldCert, List.of(expiredCandidate));

        assertThat(result.decision()).isEqualTo(MatchDecision.NO_MATCH);
        assertThat(result.matchedCandidate()).isEmpty();
        assertThat(result.decisionSummary()).contains("expired");
    }

    @Test
    @DisplayName("Scenario 10: Wrong certificate results in NO_MATCH")
    void scenario10_wrongCertificate() {
        CertificateRecord oldCert = createCert("internal.company.com", "internal.company.com",
                "ORD-1", "CN=Sectigo CA", Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));

        CertificateRecord wrongCert = createCert("public.different.org", "public.different.org",
                "ORD-99", "CN=Entrust", Instant.now(), Instant.now().plus(365, ChronoUnit.DAYS));

        CandidateMatchResult result = matcher.match(oldCert, List.of(wrongCert));

        assertThat(result.decision()).isEqualTo(MatchDecision.NO_MATCH);
        assertThat(result.matchScore()).isLessThan(properties.getReviewThreshold());
    }

    @Test
    @DisplayName("Scenario 11: Case differences are normalized to match")
    void scenario11_caseDifferences() {
        CertificateRecord oldCert = createCert("API.EXAMPLE.COM", "API.EXAMPLE.COM, PORTAL.EXAMPLE.COM",
                "ORD-CASE", "CN=Sectigo CA", Instant.now().minus(200, ChronoUnit.DAYS), Instant.now().plus(10, ChronoUnit.DAYS));

        CertificateRecord newCert = createCert("api.example.com", "api.example.com, portal.example.com",
                "ORD-CASE", "CN=Sectigo CA", Instant.now(), Instant.now().plus(730, ChronoUnit.DAYS));

        CandidateMatchResult result = matcher.match(oldCert, List.of(newCert));

        assertThat(result.decision()).isEqualTo(MatchDecision.AUTOMATIC_MATCH);
        assertThat(result.matchScore()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Scenario 12: DNS normalization handles whitespace, ports, and trailing dots")
    void scenario12_dnsNormalization() {
        CertificateRecord oldCert = createCert("  api.example.com.  ", "api.example.com.:443, DNS:portal.example.com.",
                "ORD-NORM", "CN=Sectigo CA", Instant.now().minus(200, ChronoUnit.DAYS), Instant.now().plus(10, ChronoUnit.DAYS));

        CertificateRecord newCert = createCert("api.example.com", "api.example.com, portal.example.com",
                "ORD-NORM", "CN=Sectigo CA", Instant.now(), Instant.now().plus(730, ChronoUnit.DAYS));

        CandidateMatchResult result = matcher.match(oldCert, List.of(newCert));

        assertThat(result.decision()).isEqualTo(MatchDecision.AUTOMATIC_MATCH);
        assertThat(result.matchScore()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Scenario 13: Duplicate candidates in pool are deduplicated and deterministic")
    void scenario13_duplicateCandidates() {
        CertificateRecord oldCert = createCert("dedup.example.com", "dedup.example.com",
                "ORD-DEDUP", "CN=Sectigo CA", Instant.now().minus(200, ChronoUnit.DAYS), Instant.now().plus(10, ChronoUnit.DAYS));

        CertificateRecord cand = createCert("dedup.example.com", "dedup.example.com",
                "ORD-DEDUP", "CN=Sectigo CA", Instant.now(), Instant.now().plus(730, ChronoUnit.DAYS));

        // Pass candidate 3 times
        CandidateMatchResult result = matcher.match(oldCert, List.of(cand, cand, cand));

        assertThat(result.decision()).isEqualTo(MatchDecision.AUTOMATIC_MATCH);
        assertThat(result.matchedCandidate()).contains(cand);
        // Ranked candidates must contain only 1 instance
        assertThat(result.rankedCandidates()).hasSize(1);
    }

    private CertificateRecord createCert(
            String cn, String sans, String externalId, String issuer, Instant validFrom, Instant validTo
    ) {
        CertificateRecord record = new CertificateRecord();
        UUID id = UUID.randomUUID();
        record.setId(id);
        record.setCommonName(cn);
        record.setSubjectAlternativeNames(sans);
        record.setExternalId(externalId);
        record.setIssuer(issuer);
        record.setValidFrom(validFrom);
        record.setValidTo(validTo);
        String hexId = id.toString().replace("-", "").substring(0, 16);
        record.setSerialNumber("SN-" + hexId);
        record.setThumbprint("THUMB-" + hexId);
        record.setFingerprintSha256("THUMB-" + hexId);
        record.setSource(CertificateSource.SECTIGO);
        record.setStatus(CertificateStatus.ACTIVE);
        return record;
    }
}
