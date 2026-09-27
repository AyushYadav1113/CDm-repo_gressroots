package com.grassroots.cdm.matching;

import com.grassroots.cdm.AbstractIntegrationTest;
import com.grassroots.cdm.entity.AuditLogRecord;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.CertificateReplacement;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.entity.enums.MatchStatus;
import com.grassroots.cdm.matching.model.CandidateMatchResult;
import com.grassroots.cdm.matching.model.MatchDecision;
import com.grassroots.cdm.matching.service.CertificateMatchingService;
import com.grassroots.cdm.repository.AuditLogRecordRepository;
import com.grassroots.cdm.repository.CertificateRecordRepository;
import com.grassroots.cdm.repository.CertificateReplacementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateMatchingServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CertificateMatchingService certificateMatchingService;

    @Autowired
    private CertificateRecordRepository certificateRecordRepository;

    @Autowired
    private CertificateReplacementRepository certificateReplacementRepository;

    @Autowired
    private AuditLogRecordRepository auditLogRecordRepository;

    @BeforeEach
    void cleanUp() {
        certificateReplacementRepository.deleteAll();
        certificateRecordRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-End Match: old certificate matched to new Sectigo cert, persists CertificateReplacement with JSON reasons")
    void matchCertificate_persistsReplacementRecord() {
        CertificateRecord oldCert = new CertificateRecord();
        oldCert.setCommonName("api.grassroots.internal");
        oldCert.setSubjectAlternativeNames("api.grassroots.internal, internal.grassroots.com");
        oldCert.setExternalId("sys-cert-001");
        oldCert.setIssuer("Sectigo RSA Organization Validation Secure Server CA");
        oldCert.setSerialNumber("OLDSERIAL112233");
        oldCert.setThumbprint("OLDTHUMB11223344556677889900AABBCCDDEEFF");
        oldCert.setFingerprintSha256("OLDTHUMB11223344556677889900AABBCCDDEEFF");
        oldCert.setValidFrom(Instant.now().minus(300, ChronoUnit.DAYS));
        oldCert.setValidTo(Instant.now().plus(10, ChronoUnit.DAYS));
        oldCert.setSource(CertificateSource.SERVICENOW);
        oldCert.setStatus(CertificateStatus.EXPIRING);
        oldCert = certificateRecordRepository.saveAndFlush(oldCert);

        CertificateRecord newCert = new CertificateRecord();
        newCert.setCommonName("api.grassroots.internal");
        newCert.setSubjectAlternativeNames("api.grassroots.internal, internal.grassroots.com");
        newCert.setExternalId("sectigo-order-5001-sys-cert-001");
        newCert.setIssuer("Sectigo RSA Organization Validation Secure Server CA");
        newCert.setSerialNumber("NEWSERIAL445566");
        newCert.setThumbprint("NEWTHUMB11223344556677889900AABBCCDDEEFF");
        newCert.setFingerprintSha256("NEWTHUMB11223344556677889900AABBCCDDEEFF");
        newCert.setValidFrom(Instant.now());
        newCert.setValidTo(Instant.now().plus(730, ChronoUnit.DAYS));
        newCert.setSource(CertificateSource.SECTIGO);
        newCert.setStatus(CertificateStatus.ACTIVE);
        newCert = certificateRecordRepository.saveAndFlush(newCert);

        CandidateMatchResult result = certificateMatchingService.matchCertificate(oldCert.getId(), "CORR-MATCH-E2E");

        assertThat(result.decision()).isEqualTo(MatchDecision.AUTOMATIC_MATCH);
        assertThat(result.matchScore()).isGreaterThanOrEqualTo(0.85);
        assertThat(result.matchedCandidate()).contains(newCert);

        // Verify CertificateReplacement record in PostgreSQL
        List<CertificateReplacement> replacements = certificateReplacementRepository.findByOldCertificateId(oldCert.getId());
        assertThat(replacements).hasSize(1);
        CertificateReplacement replacement = replacements.get(0);
        assertThat(replacement.getNewCertificate().getId()).isEqualTo(newCert.getId());
        assertThat(replacement.getMatchStatus()).isEqualTo(MatchStatus.AUTO_MATCHED);
        assertThat(replacement.getMatchingScore()).isGreaterThanOrEqualTo(0.85);

        // Verify JSON reasons
        assertThat(replacement.getMatchingReasons()).contains("SAN_MATCH");
        assertThat(replacement.getMatchingReasons()).contains("COMMON_NAME_MATCH");

        // Verify Audit Log
        List<AuditLogRecord> audits = auditLogRecordRepository.findByCorrelationId("CORR-MATCH-E2E");
        assertThat(audits).isNotEmpty();
        assertThat(audits.get(0).getAction()).isEqualTo("CERTIFICATE_MATCHED");
        assertThat(audits.get(0).getOutcome()).isEqualTo("AUTOMATIC_MATCH");
    }

    @Test
    @DisplayName("Ambiguous Match: two closely scored candidates flag match as PENDING_REVIEW")
    void matchCertificate_ambiguous_persistsReviewRequired() {
        CertificateRecord oldCert = new CertificateRecord();
        oldCert.setCommonName("auth.grassroots.internal");
        oldCert.setSubjectAlternativeNames("auth.grassroots.internal");
        oldCert.setExternalId("sys-auth-01");
        oldCert.setIssuer("Sectigo RSA CA");
        oldCert.setSerialNumber("OLD-AUTH-1");
        oldCert.setThumbprint("OLDTHUMB-AUTH-1111");
        oldCert.setFingerprintSha256("OLDTHUMB-AUTH-1111");
        oldCert.setValidFrom(Instant.now().minus(200, ChronoUnit.DAYS));
        oldCert.setValidTo(Instant.now().plus(10, ChronoUnit.DAYS));
        oldCert.setSource(CertificateSource.SERVICENOW);
        oldCert.setStatus(CertificateStatus.EXPIRING);
        oldCert = certificateRecordRepository.saveAndFlush(oldCert);

        CertificateRecord cand1 = new CertificateRecord();
        cand1.setCommonName("auth.grassroots.internal");
        cand1.setSubjectAlternativeNames("auth.grassroots.internal");
        cand1.setExternalId("sec-auth-cand-1");
        cand1.setIssuer("Sectigo RSA CA");
        cand1.setSerialNumber("NEW-AUTH-1");
        cand1.setThumbprint("NEWTHUMB-AUTH-1111");
        cand1.setFingerprintSha256("NEWTHUMB-AUTH-1111");
        cand1.setValidFrom(Instant.now());
        cand1.setValidTo(Instant.now().plus(730, ChronoUnit.DAYS));
        cand1.setSource(CertificateSource.SECTIGO);
        cand1.setStatus(CertificateStatus.ACTIVE);
        certificateRecordRepository.saveAndFlush(cand1);

        CertificateRecord cand2 = new CertificateRecord();
        cand2.setCommonName("auth.grassroots.internal");
        cand2.setSubjectAlternativeNames("auth.grassroots.internal");
        cand2.setExternalId("sec-auth-cand-2");
        cand2.setIssuer("Sectigo RSA CA");
        cand2.setSerialNumber("NEW-AUTH-2");
        cand2.setThumbprint("NEWTHUMB-AUTH-2222");
        cand2.setFingerprintSha256("NEWTHUMB-AUTH-2222");
        cand2.setValidFrom(Instant.now());
        cand2.setValidTo(Instant.now().plus(700, ChronoUnit.DAYS));
        cand2.setSource(CertificateSource.SECTIGO);
        cand2.setStatus(CertificateStatus.ACTIVE);
        certificateRecordRepository.saveAndFlush(cand2);

        CandidateMatchResult result = certificateMatchingService.matchCertificate(oldCert.getId(), "CORR-AMBIG");

        // Must be REVIEW_REQUIRED due to ambiguity
        assertThat(result.decision()).isEqualTo(MatchDecision.REVIEW_REQUIRED);

        Optional<CertificateReplacement> replacementOpt = certificateMatchingService.getReplacementForOldCertificate(oldCert.getId());
        assertThat(replacementOpt).isPresent();
        assertThat(replacementOpt.get().getMatchStatus()).isEqualTo(MatchStatus.PENDING_REVIEW);
    }
}
