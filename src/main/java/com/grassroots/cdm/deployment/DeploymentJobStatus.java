package com.grassroots.cdm.deployment;

/**
 * State machine status values for deployment jobs.
 */
public enum DeploymentJobStatus {
    PENDING,
    MATCHED,
    CREDENTIAL_ACQUIRED,
    DISPATCHED,
    IN_PROGRESS,
    VERIFYING,
    COMPLETED,
    FAILED,
    RETRY_PENDING,
    CANCELLED,
    ROLLED_BACK
}
