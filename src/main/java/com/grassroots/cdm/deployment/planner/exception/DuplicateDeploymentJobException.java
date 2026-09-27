package com.grassroots.cdm.deployment.planner.exception;

import java.util.UUID;

/**
 * Thrown when an active or pending deployment job already exists with the same idempotency key
 * or for the same installation and target certificate.
 */
public class DuplicateDeploymentJobException extends DeploymentPlanningException {

    private final String idempotencyKey;
    private final UUID existingJobId;

    public DuplicateDeploymentJobException(String message) {
        super(message);
        this.idempotencyKey = null;
        this.existingJobId = null;
    }

    public DuplicateDeploymentJobException(String idempotencyKey, UUID existingJobId, String message) {
        super(message);
        this.idempotencyKey = idempotencyKey;
        this.existingJobId = existingJobId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getExistingJobId() {
        return existingJobId;
    }
}
