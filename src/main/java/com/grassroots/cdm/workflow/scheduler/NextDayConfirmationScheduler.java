package com.grassroots.cdm.workflow.scheduler;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.metrics.CdmMetricsService;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import com.grassroots.cdm.verification.NextDayDiscoveryVerifier;
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
 * Scheduled workflow running daily to confirm that ServiceNow Discovery
 * has independently scanned and reconciled newly deployed certificates into CMDB.
 */
@Component
@ConditionalOnProperty(name = "cdm.orchestration.next-day-confirmation.enabled", havingValue = "true", matchIfMissing = true)
public class NextDayConfirmationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NextDayConfirmationScheduler.class);

    private final DeploymentJobRepository deploymentJobRepository;
    private final NextDayDiscoveryVerifier nextDayDiscoveryVerifier;
    private final AuditService auditService;
    private final CdmMetricsService metricsService;

    public NextDayConfirmationScheduler(
            DeploymentJobRepository deploymentJobRepository,
            NextDayDiscoveryVerifier nextDayDiscoveryVerifier,
            AuditService auditService,
            CdmMetricsService metricsService
    ) {
        this.deploymentJobRepository = deploymentJobRepository;
        this.nextDayDiscoveryVerifier = nextDayDiscoveryVerifier;
        this.auditService = auditService;
        this.metricsService = metricsService;
    }

    @Scheduled(cron = "${cdm.orchestration.next-day-confirmation.cron:0 0 4 * * ?}")
    public void runScheduledNextDayConfirmation() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        Instant start = Instant.now();
        log.info("Starting scheduled next-day ServiceNow discovery confirmation workflow (correlationId={})", correlationId);

        try {
            List<DeploymentJob> completedJobs = deploymentJobRepository.findByStatus(DeploymentJobStatus.COMPLETED);
            int reconciledCount = 0;
            int pendingCount = 0;

            for (DeploymentJob job : completedJobs) {
                boolean reconciled = nextDayDiscoveryVerifier.verifyDiscoveryReconciliation(job.getId());
                if (reconciled) {
                    reconciledCount++;
                } else {
                    pendingCount++;
                }
            }

            Duration duration = Duration.between(start, Instant.now());
            metricsService.recordWorkflowDuration("next_day_confirmation", duration);

            auditService.recordAudit(AuditEvent.create(
                    AuditAction.DISCOVERY_RECONCILED,
                    "ServiceNowCMDB",
                    "NEXT-DAY-" + correlationId,
                    "SCHEDULER",
                    "SUCCESS",
                    String.format("Next-day reconciliation evaluated %d completed jobs: reconciled=%d, pending=%d (took %d ms)",
                            completedJobs.size(), reconciledCount, pendingCount, duration.toMillis()),
                    "127.0.0.1"
            ));

            log.info("Completed next-day confirmation run (evaluated={}, reconciled={}, pending={}, duration={}ms)",
                    completedJobs.size(), reconciledCount, pendingCount, duration.toMillis());
        } catch (Exception ex) {
            Duration duration = Duration.between(start, Instant.now());
            metricsService.recordWorkflowDuration("next_day_confirmation", duration);
            log.error("Error during scheduled next-day discovery confirmation: {}", ex.getMessage(), ex);

            auditService.recordAudit(AuditEvent.create(
                    AuditAction.DISCOVERY_RECONCILED,
                    "ServiceNowCMDB",
                    "NEXT-DAY-" + correlationId,
                    "SCHEDULER",
                    "FAILURE",
                    "Scheduled next-day confirmation failed: " + ex.getMessage(),
                    "127.0.0.1"
            ));
        } finally {
            MDC.remove("correlationId");
        }
    }
}
