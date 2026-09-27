package com.grassroots.cdm.matching.model;

import com.grassroots.cdm.entity.CertificateRecord;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates the score breakdown, matching reasons, and eligibility for a candidate certificate.
 */
public record ScoredCandidate(
        CertificateRecord candidate,
        double totalScore,
        List<MatchReason> reasons,
        boolean disqualified,
        String disqualificationReason
) implements Comparable<ScoredCandidate> {

    public static ScoredCandidate disqualified(CertificateRecord candidate, String reason) {
        return new ScoredCandidate(candidate, 0.0, Collections.emptyList(), true, reason);
    }

    public static ScoredCandidate of(CertificateRecord candidate, double score, List<MatchReason> reasons) {
        double roundedScore = Math.round(score * 10000.0) / 10000.0;
        return new ScoredCandidate(candidate, roundedScore, reasons, false, null);
    }

    @Override
    public int compareTo(ScoredCandidate other) {
        // 1. Compare totalScore descending
        int scoreCompare = Double.compare(other.totalScore(), this.totalScore());
        if (scoreCompare != 0) {
            return scoreCompare;
        }

        // 2. Deterministic tie-breaker: validTo date descending (newer expiration preferred)
        if (this.candidate != null && other.candidate != null) {
            if (this.candidate.getValidTo() != null && other.candidate.getValidTo() != null) {
                int dateCompare = other.candidate.getValidTo().compareTo(this.candidate.getValidTo());
                if (dateCompare != 0) {
                    return dateCompare;
                }
            }

            // 3. Deterministic tie-breaker: external ID alphabetically
            String id1 = this.candidate.getExternalId() != null ? this.candidate.getExternalId() : "";
            String id2 = other.candidate.getExternalId() != null ? other.candidate.getExternalId() : "";
            int idCompare = id1.compareTo(id2);
            if (idCompare != 0) {
                return idCompare;
            }

            // 4. Deterministic tie-breaker: thumbprint alphabetically
            String t1 = this.candidate.getThumbprint() != null ? this.candidate.getThumbprint() : "";
            String t2 = other.candidate.getThumbprint() != null ? other.candidate.getThumbprint() : "";
            return t1.compareTo(t2);
        }

        return 0;
    }
}
