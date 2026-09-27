package com.grassroots.cdm.deployment.workflow.exception;

import com.grassroots.cdm.deployment.DeploymentJobStatus;

import java.util.UUID;

/**
 * Thrown when an invalid or arbitrary state transition is attempted on a DeploymentJob.
 */
public class InvalidStateTransitionException extends WorkflowExecutionException {

    private final UUID jobId;
    private final DeploymentJobStatus fromStatus;
    private final DeploymentJobStatus toStatus;

    public InvalidStateTransitionException(UUID jobId, DeploymentJobStatus fromStatus, DeploymentJobStatus toStatus, String message) {
        super(String.format("Invalid state transition for job %s: cannot transition from %s to %s. %s",
                jobId, fromStatus, toStatus, message));
        this.jobId = jobId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public UUID getJobId() {
        return jobId;
    }

    public DeploymentJobStatus getFromStatus() {
        return fromStatus;
    }

    public DeploymentJobStatus getToStatus() {
        return toStatus;
    }
}
