package com.grassroots.cdm.matching.impl;

import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.matching.CertificateMatcher;
import com.grassroots.cdm.matching.config.MatchingProperties;
import com.grassroots.cdm.matching.evaluator.MatchSignalEvaluator;
import com.grassroots.cdm.matching.generator.CandidateGenerator;
import com.grassroots.cdm.matching.model.CandidateMatchResult;
import com.grassroots.cdm.matching.model.MatchDecision;
import com.grassroots.cdm.matching.model.MatchReason;
import com.grassroots.cdm.matching.model.ScoredCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Production scoring-based implementation of {@link CertificateMatcher}.
 * Executes multi-attribute evaluation across SANs, Common Name, CA renewal linkage,
 * lifecycle validity, and issuer continuity with deterministic tie-breaking and ambiguity detection.
 */
@Component
public class ScoringCertificateMatcher implements CertificateMatcher {

    private static final Logger log = LoggerFactory.getLogger(ScoringCertificateMatcher.class);

    private final MatchingProperties properties;
    private final CandidateGenerator candidateGenerator;
    private final List<MatchSignalEvaluator> evaluators;

    public ScoringCertificateMatcher(
            MatchingProperties properties,
            CandidateGenerator candidateGenerator,
            List<MatchSignalEvaluator> evaluators
    ) {
        this.properties = properties;
        this.candidateGenerator = candidateGenerator;
        this.evaluators = evaluators != null ? evaluators : List.of();
    }

    @Override
    public CandidateMatchResult match(CertificateRecord oldCertificate, List<CertificateRecord> candidateCertificates) {
        if (oldCertificate == null) {
            return CandidateMatchResult.noMatch(null, "Existing certificate is null");
        }

        // 1. Candidate Generation & Pre-screening
        List<CertificateRecord> candidates = candidateGenerator.generateCandidates(oldCertificate, candidateCertificates);
        if (candidates.isEmpty()) {
            log.info("No candidates generated for certificate cn={}, id={}",
                    oldCertificate.getCommonName(), oldCertificate.getId());
            return CandidateMatchResult.noMatch(oldCertificate, "No eligible replacement candidates available in pool");
        }

        // 2. Multi-Attribute Scoring
        Instant now = Instant.now();
        List<ScoredCandidate> scoredCandidates = new ArrayList<>();

        for (CertificateRecord candidate : candidates) {
            // Check for strict expiration disqualification
            if (properties.isRejectExpiredCandidates()
                    && candidate.getValidTo() != null
                    && candidate.getValidTo().isBefore(now)) {
                log.debug("Candidate id={}, cn={} disqualified because it is expired ({})",
                        candidate.getId(), candidate.getCommonName(), candidate.getValidTo());
                scoredCandidates.add(ScoredCandidate.disqualified(candidate,
                        "Candidate certificate is expired (validTo: " + candidate.getValidTo() + ")"));
                continue;
            }

            double totalScore = 0.0;
            List<MatchReason> reasons = new ArrayList<>();

            for (MatchSignalEvaluator evaluator : evaluators) {
                MatchReason reason = evaluator.evaluate(oldCertificate, candidate);
                reasons.add(reason);
                totalScore += reason.weightedScore();
            }

            // Clamp total score strictly between 0.0 and 1.0
            double clampedScore = Math.max(0.0, Math.min(1.0, totalScore));
            scoredCandidates.add(ScoredCandidate.of(candidate, clampedScore, reasons));
        }

        // 3. Deterministic Ranking
        Collections.sort(scoredCandidates);

        // Filter valid candidates for decision making
        List<ScoredCandidate> eligibleCandidates = scoredCandidates.stream()
                .filter(c -> !c.disqualified())
                .toList();

        if (eligibleCandidates.isEmpty()) {
            return CandidateMatchResult.noMatch(oldCertificate,
                    "All candidate certificates were disqualified or expired");
        }

        ScoredCandidate topCandidate = eligibleCandidates.get(0);
        double topScore = topCandidate.totalScore();

        // 4. Threshold & Decision Evaluation
        if (topScore < properties.getReviewThreshold()) {
            log.info("Top candidate cn={} score {:.4f} below review threshold {:.4f} for old cert cn={}",
                    topCandidate.candidate().getCommonName(), topScore, properties.getReviewThreshold(),
                    oldCertificate.getCommonName());
            return CandidateMatchResult.noMatch(oldCertificate,
                    String.format("Top candidate score %.2f is below minimum review threshold %.2f",
                            topScore, properties.getReviewThreshold()));
        }

        // 5. Ambiguity Detection (Requirement: Prevent ambiguous matches from being auto-deployed)
        boolean isAmbiguous = false;
        String ambiguityReason = null;

        if (eligibleCandidates.size() > 1) {
            ScoredCandidate secondCandidate = eligibleCandidates.get(1);
            double scoreDifference = topScore - secondCandidate.totalScore();

            if (scoreDifference <= properties.getAmbiguityMargin()) {
                isAmbiguous = true;
                ambiguityReason = String.format(
                        "Ambiguous match: Top candidate score %.4f is within margin %.4f of second candidate score %.4f (%s vs %s)",
                        topScore, properties.getAmbiguityMargin(), secondCandidate.totalScore(),
                        topCandidate.candidate().getCommonName(), secondCandidate.candidate().getCommonName());
                log.warn("Ambiguity detected for old cert cn={}: {}", oldCertificate.getCommonName(), ambiguityReason);
            }
        }

        MatchDecision decision;
        String summary;

        if (isAmbiguous) {
            decision = MatchDecision.REVIEW_REQUIRED;
            summary = ambiguityReason;
        } else if (topScore >= properties.getAutoMatchThreshold()) {
            decision = MatchDecision.AUTOMATIC_MATCH;
            summary = String.format("High confidence match (score %.4f >= auto-match threshold %.4f)",
                    topScore, properties.getAutoMatchThreshold());
        } else {
            decision = MatchDecision.REVIEW_REQUIRED;
            summary = String.format("Moderate confidence match (score %.4f >= review threshold %.4f, below auto-match %.4f)",
                    topScore, properties.getReviewThreshold(), properties.getAutoMatchThreshold());
        }

        log.info("Matching evaluated: oldCn={}, newCn={}, score={:.4f}, decision={}",
                oldCertificate.getCommonName(), topCandidate.candidate().getCommonName(), topScore, decision);

        return CandidateMatchResult.of(
                oldCertificate,
                topCandidate.candidate(),
                decision,
                topScore,
                topCandidate.reasons(),
                scoredCandidates,
                summary
        );
    }
}
