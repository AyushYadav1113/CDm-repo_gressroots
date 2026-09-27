package com.grassroots.cdm.matching.model;

/**
 * Detailed evaluation outcome for a single matching signal.
 * Serialized into JSON for the matching_reasons column in PostgreSQL certificate_replacements.
 */
public record MatchReason(
        MatchSignal signal,
        double rawScore,
        double weight,
        double weightedScore,
        String description
) {
    public static MatchReason of(MatchSignal signal, double rawScore, double weight, String description) {
        double weighted = Math.round(rawScore * weight * 10000.0) / 10000.0;
        return new MatchReason(signal, rawScore, weight, weighted, description);
    }
}
