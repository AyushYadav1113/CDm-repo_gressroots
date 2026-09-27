package com.grassroots.cdm.matching.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grassroots.cdm.entity.CertificateRecord;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Result of evaluating an old certificate against a pool of replacement candidate certificates.
 * Contains the final decision, computed score, structured reasons, and ranked candidates.
 */
public record CandidateMatchResult(
        CertificateRecord oldCertificate,
        Optional<CertificateRecord> matchedCandidate,
        MatchDecision decision,
        double matchScore,
        List<MatchReason> reasons,
        String reasonsJson,
        List<ScoredCandidate> rankedCandidates,
        String decisionSummary
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static CandidateMatchResult noMatch(CertificateRecord oldCert, String summary) {
        String json = serializeJson(Map.of(
                "decision", MatchDecision.NO_MATCH.name(),
                "score", 0.0,
                "summary", summary,
                "signals", Collections.emptyList()
        ));
        return new CandidateMatchResult(
                oldCert,
                Optional.empty(),
                MatchDecision.NO_MATCH,
                0.0,
                Collections.emptyList(),
                json,
                Collections.emptyList(),
                summary
        );
    }

    public static CandidateMatchResult of(
            CertificateRecord oldCert,
            CertificateRecord matchedCandidate,
            MatchDecision decision,
            double matchScore,
            List<MatchReason> reasons,
            List<ScoredCandidate> rankedCandidates,
            String decisionSummary
    ) {
        String json = serializeJson(Map.of(
                "decision", decision.name(),
                "score", matchScore,
                "summary", decisionSummary,
                "signals", reasons != null ? reasons : Collections.emptyList()
        ));
        return new CandidateMatchResult(
                oldCert,
                Optional.ofNullable(matchedCandidate),
                decision,
                matchScore,
                reasons != null ? reasons : Collections.emptyList(),
                json,
                rankedCandidates != null ? rankedCandidates : Collections.emptyList(),
                decisionSummary
        );
    }

    private static String serializeJson(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"summary\":\"Matching evaluated\"}";
        }
    }
}
