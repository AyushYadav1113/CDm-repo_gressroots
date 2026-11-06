package com.grassroots.cdm.workflow.scheduler;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.confirmation.ConfirmationStatus;
import com.grassroots.cdm.confirmation.NextDayConfirmationService;
import com.grassroots.cdm.confirmation.model.NextDayConfirmationResult;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Scheduled workflow running daily to confirm that ServiceNow Discovery has
 * independently scanned and reconciled newly deployed certificates into CMDB.
 *
 * <p>This scheduler delegates all business logic to {@link NextDayConfirmationService}
 * and is responsible only for scheduling, metrics, and top-level audit emission.</p>
 */
@Component
@ConditionalOnProperty(
        name = "cdm.orchestration.next-day-confirmation.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NextDayConfirmationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NextDayConfirmationScheduler.class);

    private final NextDayConfirmationService confirmationService;
    private final AuditService auditService;
    private final CdmMetricsService metricsService;

    public NextDayConfirmationScheduler(
            NextDayConfirmationService confirmationService,
            AuditService auditService,
            CdmMetricsService metricsService
    ) {
        this.confirmationService = confirmationService;
        this.auditService        = auditService;
        this.metricsService      = metricsService;
    }

    @Scheduled(cron = "${cdm.orchestration.next-day-confirmation.cron:0 0 4 * * ?}")
    public void runScheduledNextDayConfirmation() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        Instant start = Instant.now();
        log.info("[{}] Starting scheduled next-day certificate confirmation workflow", correlationId);

        try {
            List<NextDayConfirmationResult> results =
                    confirmationService.processAllPendingConfirmations(correlationId);

            Duration duration = Duration.between(start, Instant.now());
            metricsService.recordWorkflowDuration("next_day_confirmation", duration);

            // Tally outcomes
            Map<ConfirmationStatus, Long> tally = results.stream()
                    .collect(Collectors.groupingBy(NextDayConfirmationResult::getStatus, Collectors.counting()));

            long confirmed   = tally.getOrDefault(ConfirmationStatus.CONFIRMED, 0L);
            long failed      = tally.getOrDefault(ConfirmationStatus.CONFIRMATION_FAILED, 0L);
            long unavailable = tally.getOrDefault(ConfirmationStatus.DISCOVERY_UNAVAILABLE, 0L);
            long manual      = tally.getOrDefault(ConfirmationStatus.MANUAL_REVIEW, 0L);
            long pending     = tally.getOrDefault(ConfirmationStatus.PENDING_CONFIRMATION, 0L);

            // Record per-status metrics
            if (confirmed > 0)   metricsService.recordCertificatesMatched("NEXT_DAY_CONFIRMED", (int) confirmed);
            if (failed > 0)      metricsService.recordDeploymentFailed("CONFIRMATION_FAILED", "NEXT_DAY");
            if (unavailable > 0) metricsService.recordVerificationFailure("DISCOVERY_UNAVAILABLE");

            auditService.recordAudit(AuditEvent.create(
                    AuditAction.DISCOVERY_RECONCILED,
                    "ServiceNowCMDB",
                    "NEXT-DAY-" + correlationId,
                    "SCHEDULER",
                    failed > 0 || manual > 0 ? "PARTIAL" : "SUCCESS",
                    String.format(
                            "Next-day confirmation: evaluated=%d confirmed=%d failed=%d unavailable=%d manual=%d pending=%d (took %d ms)",
                            results.size(), confirmed, failed, unavailable, manual, pending, duration.toMillis()),
                    "127.0.0.1"
            ));

            log.info("[{}] Completed next-day confirmation (evaluated={}, confirmed={}, failed={}, unavailable={}, manual={}, pending={}, duration={}ms)",
                    correlationId, results.size(), confirmed, failed, unavailable, manual, pending, duration.toMillis());

        } catch (Exception ex) {
            Duration duration = Duration.between(start, Instant.now());
            metricsService.recordWorkflowDuration("next_day_confirmation", duration);
            log.error("[{}] Error during scheduled next-day confirmation: {}", correlationId, ex.getMessage(), ex);

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
