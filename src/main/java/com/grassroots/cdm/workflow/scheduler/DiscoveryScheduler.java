package com.grassroots.cdm.workflow.scheduler;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.discovery.DiscoveryService;
import com.grassroots.cdm.dto.DiscoveryResultDto;
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
 * Scheduled workflow driving automated ServiceNow CMDB certificate discovery.
 */
@Component
@ConditionalOnProperty(name = "cdm.orchestration.discovery.enabled", havingValue = "true", matchIfMissing = true)
public class DiscoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryScheduler.class);

    private final DiscoveryService discoveryService;
    private final AuditService auditService;
    private final CdmMetricsService metricsService;

    public DiscoveryScheduler(
            DiscoveryService discoveryService,
            AuditService auditService,
            CdmMetricsService metricsService
    ) {
        this.discoveryService = discoveryService;
        this.auditService = auditService;
        this.metricsService = metricsService;
    }

    @Scheduled(cron = "${cdm.orchestration.discovery.cron:0 0 1 * * ?}")
    public void runScheduledDiscovery() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        Instant start = Instant.now();
        log.info("Starting scheduled ServiceNow certificate discovery workflow (correlationId={})", correlationId);

        try {
            DiscoveryResultDto result = discoveryService.discoverCertificates(correlationId);
            Duration duration = Duration.between(start, Instant.now());
            metricsService.recordWorkflowDuration("discovery", duration);

            int totalDiscovered = result.totalDiscovered();
            metricsService.recordCertificatesDiscovered("SERVICENOW", totalDiscovered);

            auditService.recordAudit(AuditEvent.create(
                    AuditAction.CERTIFICATE_DISCOVERED,
                    "ServiceNowCMDB",
                    "DISCOVERY-" + correlationId,
                    "SCHEDULER",
                    "SUCCESS",
                    String.format("Discovered %d certificates from ServiceNow in %d ms", totalDiscovered, duration.toMillis()),
                    "127.0.0.1"
            ));

            log.info("Completed scheduled ServiceNow certificate discovery (discovered={}, duration={}ms)",
                    totalDiscovered, duration.toMillis());
        } catch (Exception ex) {
            Duration duration = Duration.between(start, Instant.now());
            metricsService.recordWorkflowDuration("discovery", duration);
            log.error("Error during scheduled ServiceNow discovery: {}", ex.getMessage(), ex);

            auditService.recordAudit(AuditEvent.create(
                    AuditAction.CERTIFICATE_DISCOVERED,
                    "ServiceNowCMDB",
                    "DISCOVERY-" + correlationId,
                    "SCHEDULER",
                    "FAILURE",
                    "Scheduled discovery failed: " + ex.getMessage(),
                    "127.0.0.1"
            ));
        } finally {
            MDC.remove("correlationId");
        }
    }
}
