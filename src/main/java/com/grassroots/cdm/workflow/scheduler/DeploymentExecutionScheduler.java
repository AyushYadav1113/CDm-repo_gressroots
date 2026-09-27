package com.grassroots.cdm.workflow.scheduler;

import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.metrics.CdmMetricsService;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import com.grassroots.cdm.workflow.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled workflow driving execution of planned deployment jobs through the lifecycle:
 * Credential Retrieval -> MID Server Dispatch -> Live Verification -> Completion.
 */
@Component
@ConditionalOnProperty(name = "cdm.orchestration.execution.enabled", havingValue = "true", matchIfMissing = true)
public class DeploymentExecutionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeploymentExecutionScheduler.class);

    private static final List<DeploymentJobStatus> EXECUTABLE_STATUSES = List.of(
            DeploymentJobStatus.PLANNED,
            DeploymentJobStatus.PENDING,
            DeploymentJobStatus.CREDENTIALS_PENDING,
            DeploymentJobStatus.CREDENTIALS_ACQUIRED,
            DeploymentJobStatus.SENT_TO_MID,
            DeploymentJobStatus.RUNNING,
            DeploymentJobStatus.DEPLOYED,
            DeploymentJobStatus.VERIFICATION_PENDING,
            DeploymentJobStatus.VERIFIED
    );

    private final DeploymentJobRepository deploymentJobRepository;
    private final WorkflowEngine workflowEngine;
    private final CdmMetricsService metricsService;

    public DeploymentExecutionScheduler(
            DeploymentJobRepository deploymentJobRepository,
            WorkflowEngine workflowEngine,
            CdmMetricsService metricsService
    ) {
        this.deploymentJobRepository = deploymentJobRepository;
        this.workflowEngine = workflowEngine;
        this.metricsService = metricsService;
    }

    @Scheduled(fixedDelayString = "${cdm.orchestration.execution.fixed-delay-ms:60000}")
    public void runScheduledExecution() {
        List<DeploymentJob> pendingJobs = deploymentJobRepository.findByStatusIn(EXECUTABLE_STATUSES);
        if (pendingJobs.isEmpty()) {
            return;
        }

        log.info("Found {} deployment jobs ready for execution processing", pendingJobs.size());
        Instant start = Instant.now();

        for (DeploymentJob job : pendingJobs) {
            try {
                workflowEngine.executeJobWorkflow(job.getId());

                DeploymentJob refreshed = deploymentJobRepository.findById(job.getId()).orElse(job);
                String tech = (refreshed.getTargetType() != null) ? refreshed.getTargetType() : "GENERIC";

                if (refreshed.getStatus() == DeploymentJobStatus.COMPLETED) {
                    metricsService.recordDeploymentSuccessful(tech);
                } else if (refreshed.getStatus() == DeploymentJobStatus.FAILED) {
                    metricsService.recordDeploymentFailed(refreshed.getErrorMessage(), tech);
                }
            } catch (Exception ex) {
                log.error("Unhandled exception during execution of job {}: {}", job.getId(), ex.getMessage(), ex);
                String tech = (job.getTargetType() != null) ? job.getTargetType() : "GENERIC";
                metricsService.recordDeploymentFailed(ex.getMessage(), tech);
            }
        }

        Duration duration = Duration.between(start, Instant.now());
        metricsService.recordWorkflowDuration("execution", duration);
        log.info("Finished execution cycle for {} jobs in {} ms", pendingJobs.size(), duration.toMillis());
    }
}
