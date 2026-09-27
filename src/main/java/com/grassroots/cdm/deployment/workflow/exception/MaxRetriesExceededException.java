package com.grassroots.cdm.deployment.workflow.exception;

import java.util.UUID;

/**
 * Thrown when a job has exhausted its configured maximum retry attempts.
 */
public class MaxRetriesExceededException extends WorkflowExecutionException {

    private final UUID jobId;
    private final int attemptCount;
    private final int maxRetries;

    public MaxRetriesExceededException(UUID jobId, int attemptCount, int maxRetries, String message) {
        super(String.format("Job %s exceeded max retries (%d/%d): %s", jobId, attemptCount, maxRetries, message));
        this.jobId = jobId;
        this.attemptCount = attemptCount;
        this.maxRetries = maxRetries;
    }

    public UUID getJobId() {
        return jobId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }
}
