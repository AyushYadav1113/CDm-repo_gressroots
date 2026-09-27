package com.grassroots.cdm.workflow.scheduler;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.deployment.workflow.DeploymentWorkflowService;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.metrics.CdmMetricsService;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import com.grassroots.cdm.workflow.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled workflow evaluating and processing RETRY_PENDING deployment jobs
 * according to exponential backoff timestamps and maximum retry thresholds.
 */
@Component
@ConditionalOnProperty(name = "cdm.orchestration.retry.enabled", havingValue = "true", matchIfMissing = true)
public class DeploymentRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeploymentRetryScheduler.class);

    private final DeploymentJobRepository deploymentJobRepository;
    private final WorkflowEngine workflowEngine;
    private final DeploymentWorkflowService workflowService;
    private final AuditService auditService;
    private final CdmMetricsService metricsService;

    public DeploymentRetryScheduler(
            DeploymentJobRepository deploymentJobRepository,
            WorkflowEngine workflowEngine,
            DeploymentWorkflowService workflowService,
            AuditService auditService,
            CdmMetricsService metricsService
    ) {
        this.deploymentJobRepository = deploymentJobRepository;
        this.workflowEngine = workflowEngine;
        this.workflowService = workflowService;
        this.auditService = auditService;
        this.metricsService = metricsService;
    }

    @Scheduled(fixedDelayString = "${cdm.orchestration.retry.fixed-delay-ms:30000}")
    public void runScheduledRetries() {
        List<DeploymentJob> retryPendingJobs = deploymentJobRepository.findByStatus(DeploymentJobStatus.RETRY_PENDING);
        if (retryPendingJobs.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        Instant start = Instant.now();

        for (DeploymentJob job : retryPendingJobs) {
            Instant scheduledAt = job.getScheduledAt();
            if (scheduledAt != null && scheduledAt.isAfter(now)) {
                // Backoff delay has not elapsed yet
                log.debug("Job {} scheduled for retry at {}, current time {}. Waiting...", job.getId(), scheduledAt, now);
                continue;
            }

            String correlationId = UUID.randomUUID().toString();
            MDC.put("correlationId", correlationId);

            try {
                if (job.getAttemptCount() >= job.getMaxRetries()) {
                    log.warn("Job {} reached max retries ({}/{}). Escalating to MANUAL_REVIEW.",
                            job.getId(), job.getAttemptCount(), job.getMaxRetries());

                    String reason = String.format("Retry threshold reached (%d/%d attempts). Last error: %s",
                            job.getAttemptCount(), job.getMaxRetries(), job.getErrorMessage());
                    workflowService.escalateToManualReview(job.getId(), reason, correlationId);

                    auditService.recordAudit(AuditEvent.create(
                            AuditAction.MANUAL_REVIEW_ESCALATED,
                            "DeploymentJob",
                            job.getId().toString(),
                            "RETRY_SCHEDULER",
                            "ESCALATED",
                            reason,
                            "127.0.0.1"
                    ));
                    continue;
                }

                log.info("Executing scheduled retry for job {} (attempt {}/{})",
                        job.getId(), job.getAttemptCount(), job.getMaxRetries());

                metricsService.recordRetryAttempt(job.getAttemptCount());

                auditService.recordAudit(AuditEvent.create(
                        AuditAction.JOB_RETRIED,
                        "DeploymentJob",
                        job.getId().toString(),
                        "RETRY_SCHEDULER",
                        "ATTEMPTING",
                        String.format("Triggering retry attempt %d of %d", job.getAttemptCount(), job.getMaxRetries()),
                        "127.0.0.1"
                ));

                workflowEngine.retryJobWorkflow(job.getId());

            } catch (Exception ex) {
                log.error("Error executing retry for job {}: {}", job.getId(), ex.getMessage(), ex);
            } finally {
                MDC.remove("correlationId");
            }
        }

        Duration duration = Duration.between(start, Instant.now());
        metricsService.recordWorkflowDuration("retry", duration);
    }
}
