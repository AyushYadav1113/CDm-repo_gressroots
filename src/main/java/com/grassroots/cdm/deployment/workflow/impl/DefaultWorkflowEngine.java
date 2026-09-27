package com.grassroots.cdm.deployment.workflow.impl;

import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.deployment.workflow.DeploymentWorkflowService;
import com.grassroots.cdm.workflow.WorkflowEngine;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrator engine driving the end-to-end multi-step certificate deployment workflow.
 */
@Service
public class DefaultWorkflowEngine implements WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkflowEngine.class);

    private final DeploymentWorkflowService workflowService;
    private final DeploymentJobRepository deploymentJobRepository;

    public DefaultWorkflowEngine(
            DeploymentWorkflowService workflowService,
            DeploymentJobRepository deploymentJobRepository
    ) {
        this.workflowService = workflowService;
        this.deploymentJobRepository = deploymentJobRepository;
    }

    @Override
    public void executeJobWorkflow(UUID jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId must not be null");
        }

        String correlationId = UUID.randomUUID().toString();
        log.info("Starting workflow execution for job {} (correlationId={})", jobId, correlationId);

        int steps = 0;
        int maxSteps = 20;

        while (steps++ < maxSteps) {
            DeploymentJob job = deploymentJobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("DeploymentJob not found for ID: " + jobId));

            DeploymentJobStatus status = job.getStatus();
            if (status == DeploymentJobStatus.COMPLETED
                    || status == DeploymentJobStatus.MANUAL_REVIEW
                    || status == DeploymentJobStatus.CANCELLED) {
                log.info("Workflow execution reached steady/terminal state {} for job {}", status, jobId);
                break;
            }

            workflowService.advanceJob(jobId, correlationId);
        }
    }

    @Override
    public void retryJobWorkflow(UUID jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId must not be null");
        }
        String correlationId = UUID.randomUUID().toString();
        log.info("Retrying workflow execution for job {} (correlationId={})", jobId, correlationId);
        workflowService.advanceJob(jobId, correlationId);
        executeJobWorkflow(jobId);
    }
}
