package com.grassroots.cdm.entity.enums;

/**
 * Status of correlation match between an old certificate and its candidate replacement.
 */
public enum MatchStatus {
    AUTO_MATCHED,
    MANUALLY_CONFIRMED,
    PENDING_REVIEW,
    REJECTED,
    SUPERSEDED
}
