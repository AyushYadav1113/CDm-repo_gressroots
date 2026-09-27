package com.grassroots.cdm.workflow.scheduler;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.dto.SectigoSyncResultDto;
import com.grassroots.cdm.integration.sectigo.SectigoIntegrationService;
import com.grassroots.cdm.metrics.CdmMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Scheduled workflow driving automated synchronization of issued/renewed certificates from Sectigo SCM.
 */
@Component
@ConditionalOnProperty(name = "cdm.orchestration.sectigo-sync.enabled", havingValue = "true", matchIfMissing = true)
public class SectigoSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SectigoSyncScheduler.class);

    private final SectigoIntegrationService sectigoIntegrationService;
    private final AuditService auditService;
    private final CdmMetricsService metricsService;

    public SectigoSyncScheduler(
            SectigoIntegrationService sectigoIntegrationService,
            AuditService auditService,
            CdmMetricsService metricsService
    ) {
        this.sectigoIntegrationService = sectigoIntegrationService;
        this.auditService = auditService;
        this.metricsService = metricsService;
    }

    @Scheduled(cron = "${cdm.orchestration.sectigo-sync.cron:0 30 1 * * ?}")
    public void runScheduledSectigoSync() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        Instant start = Instant.now();
        log.info("Starting scheduled Sectigo certificate synchronization (correlationId={})", correlationId);

        try {
            SectigoSyncResultDto result = sectigoIntegrationService.syncCertificates(correlationId);
            Duration duration = Duration.between(start, Instant.now());
            metricsService.recordWorkflowDuration("sectigo_sync", duration);

            int totalSynced = result.createdCount() + result.updatedCount();
            metricsService.recordCertificatesDiscovered("SECTIGO", totalSynced);

            auditService.recordAudit(AuditEvent.create(
                    AuditAction.CERTIFICATE_DISCOVERED,
                    "SectigoSCM",
                    "SECTIGO-SYNC-" + correlationId,
                    "SCHEDULER",
                    "SUCCESS",
                    String.format("Synced %d certificates from Sectigo in %d ms", totalSynced, duration.toMillis()),
                    "127.0.0.1"
            ));

            log.info("Completed scheduled Sectigo certificate synchronization (created={}, updated={}, duration={}ms)",
                    result.createdCount(), result.updatedCount(), duration.toMillis());
        } catch (Exception ex) {
            Duration duration = Duration.between(start, Instant.now());
            metricsService.recordWorkflowDuration("sectigo_sync", duration);
            log.error("Error during scheduled Sectigo certificate sync: {}", ex.getMessage(), ex);

            auditService.recordAudit(AuditEvent.create(
                    AuditAction.CERTIFICATE_DISCOVERED,
                    "SectigoSCM",
                    "SECTIGO-SYNC-" + correlationId,
                    "SCHEDULER",
                    "FAILURE",
                    "Scheduled Sectigo sync failed: " + ex.getMessage(),
                    "127.0.0.1"
            ));
        } finally {
            MDC.remove("correlationId");
        }
    }
}
