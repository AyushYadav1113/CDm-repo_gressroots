package com.grassroots.cdm.workflow;

import java.util.UUID;

/**
 * Orchestrator engine driving the end-to-end multi-step certificate lifecycle:
 * Discovery -> Sectigo Fetch -> Matching -> CyberArk Credential Acquisition ->
 * MID Server Dispatch -> Live Verification -> Next-Day Verification -> Audit Logging.
 */
public interface WorkflowEngine {

    /**
     * Triggers the orchestration workflow for a specific deployment job.
     *
     * @param jobId UUID of the DeploymentJob
     */
    void executeJobWorkflow(UUID jobId);

    /**
     * Retries a failed or timed-out deployment job.
     *
     * @param jobId UUID of the DeploymentJob
     */
    void retryJobWorkflow(UUID jobId);
}
