package com.grassroots.cdm.workflow.scheduler;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.matching.model.CandidateMatchResult;
import com.grassroots.cdm.matching.service.CertificateMatchingService;
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
 * Scheduled workflow driving automated matching between expiring CMDB certificates
 * and renewed certificates issued by Sectigo.
 */
@Component
@ConditionalOnProperty(name = "cdm.orchestration.matching.enabled", havingValue = "true", matchIfMissing = true)
public class CertificateMatchingScheduler {

    private static final Logger log = LoggerFactory.getLogger(CertificateMatchingScheduler.class);

    private final CertificateMatchingService matchingService;
    private final AuditService auditService;
    private final CdmMetricsService metricsService;

    public CertificateMatchingScheduler(
            CertificateMatchingService matchingService,
            AuditService auditService,
            CdmMetricsService metricsService
    ) {
        this.matchingService = matchingService;
        this.auditService = auditService;
        this.metricsService = metricsService;
    }

    @Scheduled(cron = "${cdm.orchestration.matching.cron:0 0 2 * * ?}")
    public void runScheduledMatching() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        Instant start = Instant.now();
        log.info("Starting scheduled certificate matching workflow (correlationId={})", correlationId);

        try {
            List<CandidateMatchResult> matchResults = matchingService.matchAllExpiringCertificates(correlationId);
            Duration duration = Duration.between(start, Instant.now());
            metricsService.recordWorkflowDuration("matching", duration);

            long autoMatched = matchResults.stream().filter(r -> "AUTO_MATCH".equalsIgnoreCase(r.decision().name())).count();
            long reviewRequired = matchResults.stream().filter(r -> "REVIEW_REQUIRED".equalsIgnoreCase(r.decision().name())).count();
            long noMatch = matchResults.stream().filter(r -> "NO_MATCH".equalsIgnoreCase(r.decision().name())).count();

            metricsService.recordCertificatesMatched("AUTO_MATCH", (int) autoMatched);
            metricsService.recordCertificatesMatched("REVIEW_REQUIRED", (int) reviewRequired);
            metricsService.recordCertificatesMatched("NO_MATCH", (int) noMatch);

            auditService.recordAudit(AuditEvent.create(
                    AuditAction.CERTIFICATE_MATCHED,
                    "CertificateMatchingEngine",
                    "MATCHING-" + correlationId,
                    "SCHEDULER",
                    "SUCCESS",
                    String.format("Evaluated %d expiring certificates: autoMatched=%d, reviewRequired=%d, noMatch=%d (took %d ms)",
                            matchResults.size(), autoMatched, reviewRequired, noMatch, duration.toMillis()),
                    "127.0.0.1"
            ));

            log.info("Completed scheduled certificate matching (total={}, autoMatched={}, reviewRequired={}, noMatch={}, duration={}ms)",
                    matchResults.size(), autoMatched, reviewRequired, noMatch, duration.toMillis());
        } catch (Exception ex) {
            Duration duration = Duration.between(start, Instant.now());
            metricsService.recordWorkflowDuration("matching", duration);
            log.error("Error during scheduled certificate matching: {}", ex.getMessage(), ex);

            auditService.recordAudit(AuditEvent.create(
                    AuditAction.CERTIFICATE_MATCHED,
                    "CertificateMatchingEngine",
                    "MATCHING-" + correlationId,
                    "SCHEDULER",
                    "FAILURE",
                    "Scheduled matching failed: " + ex.getMessage(),
                    "127.0.0.1"
            ));
        } finally {
            MDC.remove("correlationId");
        }
    }
}
