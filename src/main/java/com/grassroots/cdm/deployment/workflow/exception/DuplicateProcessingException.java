package com.grassroots.cdm.deployment.workflow.exception;

import java.util.UUID;

/**
 * Thrown when an execution request is submitted for a job that is already executing
 * in another thread or has already reached COMPLETED status.
 */
public class DuplicateProcessingException extends WorkflowExecutionException {

    private final UUID jobId;

    public DuplicateProcessingException(UUID jobId, String message) {
        super(String.format("Duplicate processing detected for job %s: %s", jobId, message));
        this.jobId = jobId;
    }

    public UUID getJobId() {
        return jobId;
    }
}
