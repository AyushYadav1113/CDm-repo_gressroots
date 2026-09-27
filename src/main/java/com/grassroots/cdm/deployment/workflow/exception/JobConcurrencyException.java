package com.grassroots.cdm.deployment.workflow.exception;

import java.util.UUID;

/**
 * Thrown when concurrent modifications to the same DeploymentJob are detected via optimistic locking.
 */
public class JobConcurrencyException extends WorkflowExecutionException {

    private final UUID jobId;

    public JobConcurrencyException(UUID jobId, String message, Throwable cause) {
        super(String.format("Concurrency conflict for job %s: %s", jobId, message), cause);
        this.jobId = jobId;
    }

    public UUID getJobId() {
        return jobId;
    }
}
