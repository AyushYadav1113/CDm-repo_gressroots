package com.grassroots.cdm.workflow.scheduler;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.planner.DeploymentPlanner;
import com.grassroots.cdm.deployment.planner.model.DeploymentPlanResult;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.metrics.CdmMetricsService;
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
 * Scheduled workflow driving automated creation of pending deployment jobs for matched replacements.
 */
@Component
@ConditionalOnProperty(name = "cdm.orchestration.planning.enabled", havingValue = "true", matchIfMissing = true)
public class DeploymentPlanningScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeploymentPlanningScheduler.class);

    private final DeploymentPlanner deploymentPlanner;
    private final AuditService auditService;
    private final CdmMetricsService metricsService;

    public DeploymentPlanningScheduler(
            DeploymentPlanner deploymentPlanner,
            AuditService auditService,
            CdmMetricsService metricsService
    ) {
        this.deploymentPlanner = deploymentPlanner;
        this.auditService = auditService;
        this.metricsService = metricsService;
    }

    @Scheduled(cron = "${cdm.orchestration.planning.cron:0 30 2 * * ?}")
    public void runScheduledPlanning() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        Instant start = Instant.now();
        log.info("Starting scheduled deployment planning workflow (correlationId={})", correlationId);

        try {
            List<DeploymentPlanResult> planResults = deploymentPlanner.planAllPendingDeployments();
            Duration duration = Duration.between(start, Instant.now());
            metricsService.recordWorkflowDuration("planning", duration);

            int totalJobsCreated = 0;
            for (DeploymentPlanResult result : planResults) {
                if (result.plannedJobs() != null) {
                    for (DeploymentJob job : result.plannedJobs()) {
                        totalJobsCreated++;
                        String tech = (job.getTargetType() != null) ? job.getTargetType() : "GENERIC";
                        metricsService.recordDeploymentJobCreated(tech);
                    }
                }
            }

            auditService.recordAudit(AuditEvent.create(
                    AuditAction.DEPLOYMENT_JOB_CREATED,
                    "DeploymentPlanner",
                    "PLANNING-" + correlationId,
                    "SCHEDULER",
                    "SUCCESS",
                    String.format("Planned deployments for %d replacements, created %d deployment jobs in %d ms",
                            planResults.size(), totalJobsCreated, duration.toMillis()),
                    "127.0.0.1"
            ));

            log.info("Completed scheduled deployment planning (replacements={}, jobsCreated={}, duration={}ms)",
                    planResults.size(), totalJobsCreated, duration.toMillis());
        } catch (Exception ex) {
            Duration duration = Duration.between(start, Instant.now());
            metricsService.recordWorkflowDuration("planning", duration);
            log.error("Error during scheduled deployment planning: {}", ex.getMessage(), ex);

            auditService.recordAudit(AuditEvent.create(
                    AuditAction.DEPLOYMENT_JOB_CREATED,
                    "DeploymentPlanner",
                    "PLANNING-" + correlationId,
                    "SCHEDULER",
                    "FAILURE",
                    "Scheduled deployment planning failed: " + ex.getMessage(),
                    "127.0.0.1"
            ));
        } finally {
            MDC.remove("correlationId");
        }
    }
}
