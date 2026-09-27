package com.grassroots.cdm.deployment.workflow;

import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.entity.DeploymentJob;

import java.util.UUID;

/**
 * Service contract for advancing, managing, and orchestrating deployment job lifecycle workflows.
 */
public interface DeploymentWorkflowService {

    /**
     * Advances the job to its next lifecycle stage deterministically based on its current status.
     *
     * @param jobId         UUID of the DeploymentJob
     * @param correlationId tracing correlation ID
     * @return the advanced DeploymentJob
     */
    DeploymentJob advanceJob(UUID jobId, String correlationId);

    /**
     * Executes an explicit state machine transition on a job.
     *
     * @param jobId         UUID of the DeploymentJob
     * @param targetStatus  desired target status
     * @param reason        explanation for the transition
     * @param actor         user or system identity initiating the transition
     * @param correlationId tracing correlation ID
     * @return the updated DeploymentJob
     */
    DeploymentJob transitionJob(UUID jobId, DeploymentJobStatus targetStatus, String reason, String actor, String correlationId);

    /**
     * Transitions job to CREDENTIALS_PENDING, acquires target host credentials from CyberArk vault,
     * and transitions to CREDENTIALS_ACQUIRED.
     */
    DeploymentJob acquireCredentials(UUID jobId, String correlationId);

    /**
     * Dispatches the deployment instruction payload to the MID Server queue,
     * transitioning job to SENT_TO_MID.
     */
    DeploymentJob dispatchToMidServer(UUID jobId, String correlationId);

    /**
     * Acknowledges that the MID Server has begun executing deployment on the target host,
     * transitioning job to RUNNING.
     */
    DeploymentJob acknowledgeRunning(UUID jobId, String midServerTaskId, String correlationId);

    /**
     * Marks deployment execution complete on the target server,
     * transitioning job to DEPLOYED.
     */
    DeploymentJob markDeployed(UUID jobId, String correlationId);

    /**
     * Verifies the deployed certificate via live TLS handshake,
     * transitioning job through VERIFICATION_PENDING to VERIFIED.
     */
    DeploymentJob verifyDeployment(UUID jobId, String correlationId);

    /**
     * Finalizes the deployment job as COMPLETED, updating installation and certificate statuses.
     */
    DeploymentJob completeDeployment(UUID jobId, String correlationId);

    /**
     * Handles failure during workflow execution, scheduling a retry if eligible or escalating to manual review.
     */
    DeploymentJob handleFailure(UUID jobId, String errorMessage, boolean retryable, String correlationId);

    /**
     * Schedules a failed job for retry, transitioning to RETRY_PENDING.
     */
    DeploymentJob scheduleRetry(UUID jobId, String reason, String correlationId);

    /**
     * Escalates a job to MANUAL_REVIEW.
     */
    DeploymentJob escalateToManualReview(UUID jobId, String reason, String correlationId);

    /**
     * Approves/unblocks a job from MANUAL_REVIEW, returning it to RETRY_PENDING.
     */
    DeploymentJob approveFromManualReview(UUID jobId, String reason, String actor, String correlationId);
}
