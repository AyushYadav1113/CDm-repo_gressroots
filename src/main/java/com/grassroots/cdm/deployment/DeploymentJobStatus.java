package com.grassroots.cdm.deployment;

/**
 * State machine status values for deployment jobs throughout the orchestration lifecycle.
 */
public enum DeploymentJobStatus {
    // Primary State Machine Lifecycle States
    CREATED,
    PLANNED,
    CREDENTIALS_PENDING,
    CREDENTIALS_ACQUIRED,
    SENT_TO_MID,
    RUNNING,
    DEPLOYED,
    VERIFICATION_PENDING,
    VERIFIED,
    COMPLETED,
    FAILED,
    RETRY_PENDING,
    MANUAL_REVIEW,

    // Backward-Compatibility / Aliases
    PENDING,
    MATCHED,
    CREDENTIAL_ACQUIRED,
    DISPATCHED,
    IN_PROGRESS,
    VERIFYING,
    CANCELLED,
    ROLLED_BACK;

    /**
     * Checks if this status is terminal (no further transitions permitted).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == ROLLED_BACK;
    }

    /**
     * Checks if this status indicates an active in-flight execution.
     */
    public boolean isInFlight() {
        return this == CREDENTIALS_PENDING
                || this == CREDENTIALS_ACQUIRED
                || this == SENT_TO_MID
                || this == RUNNING
                || this == IN_PROGRESS
                || this == DEPLOYED
                || this == VERIFICATION_PENDING
                || this == VERIFYING
                || this == VERIFIED;
    }
}
