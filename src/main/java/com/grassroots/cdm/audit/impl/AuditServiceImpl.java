package com.grassroots.cdm.audit.impl;

import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.entity.AuditLogRecord;
import com.grassroots.cdm.repository.AuditLogRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Production implementation of {@link AuditService} that writes tamper-evident records to PostgreSQL.
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditLogRecordRepository auditLogRecordRepository;

    public AuditServiceImpl(AuditLogRecordRepository auditLogRecordRepository) {
        this.auditLogRecordRepository = auditLogRecordRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAudit(AuditEvent event) {
        try {
            AuditLogRecord record = new AuditLogRecord();
            record.setAction(event.action().name());
            record.setEntityName(event.entityName());
            record.setEntityId(event.entityId());
            record.setActor(event.actor() != null ? event.actor() : "SYSTEM");
            record.setOutcome(event.outcome() != null ? event.outcome() : "SUCCESS");
            record.setDetails(formatJsonDetails(event.details()));
            record.setClientIp(event.clientIp());

            String correlationId = MDC.get("correlationId");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            record.setCorrelationId(correlationId);
            record.setReferenceId(event.entityId());
            record.setEventType(event.action().name());
            record.setMessage(String.format("Action %s performed on %s (%s) with outcome %s",
                    event.action(), event.entityName(), event.entityId(), event.outcome()));

            auditLogRecordRepository.save(record);
            log.info("Recorded audit log: action={}, entityName={}, entityId={}, correlationId={}",
                    event.action(), event.entityName(), event.entityId(), correlationId);
        } catch (Exception ex) {
            log.error("Failed to persist audit log entry: {}", event, ex);
        }
    }

    private String formatJsonDetails(String details) {
        if (details == null || details.isBlank()) {
            return "{}";
        }
        String trimmed = details.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return trimmed;
        }
        String sanitized = trimmed.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
        return String.format("{\"summary\":\"%s\"}", sanitized);
    }
}

