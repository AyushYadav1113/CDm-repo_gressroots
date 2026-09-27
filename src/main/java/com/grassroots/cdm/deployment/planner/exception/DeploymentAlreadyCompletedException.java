package com.grassroots.cdm.deployment.planner.exception;

import java.util.UUID;

/**
 * Thrown when an existing deployment job for the same certificate installation has already
 * reached COMPLETED status.
 */
public class DeploymentAlreadyCompletedException extends DeploymentPlanningException {

    private final UUID existingJobId;
    private final String idempotencyKey;

    public DeploymentAlreadyCompletedException(String message) {
        super(message);
        this.existingJobId = null;
        this.idempotencyKey = null;
    }

    public DeploymentAlreadyCompletedException(UUID existingJobId, String idempotencyKey, String message) {
        super(message);
        this.existingJobId = existingJobId;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getExistingJobId() {
        return existingJobId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
