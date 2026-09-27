package com.grassroots.cdm.matching.model;

/**
 * Standardized categorization of matching signals evaluated during certificate correlation.
 */
public enum MatchSignal {
    SAN_MATCH,
    COMMON_NAME_MATCH,
    RENEWAL_LINKAGE,
    LIFECYCLE_EXTENSION,
    ISSUER_CONTINUITY
}
