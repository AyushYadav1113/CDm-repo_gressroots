package com.grassroots.cdm.matching.model;

import com.grassroots.cdm.entity.enums.MatchStatus;

/**
 * High-level matching decision outcome evaluated by the matching engine.
 */
public enum MatchDecision {

    /**
     * Confident, unambiguous match meeting the automatic deployment threshold.
     */
    AUTOMATIC_MATCH,

    /**
     * Candidate meets basic match criteria but falls below auto-match threshold
     * or is ambiguous due to closely competing candidates. Requires administrator review.
     */
    REVIEW_REQUIRED,

    /**
     * No eligible candidates found or all candidates fell below the review threshold.
     */
    NO_MATCH;

    /**
     * Maps the decision to the persistent domain MatchStatus enum.
     */
    public MatchStatus toMatchStatus() {
        return switch (this) {
            case AUTOMATIC_MATCH -> MatchStatus.AUTO_MATCHED;
            case REVIEW_REQUIRED -> MatchStatus.PENDING_REVIEW;
            case NO_MATCH -> MatchStatus.REJECTED;
        };
    }
}
