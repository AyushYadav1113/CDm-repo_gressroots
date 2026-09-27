package com.grassroots.cdm.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Service contract for persisting immutable, compliance-grade audit records.
 */
public interface AuditService {

    /**
     * Records a standard immutable domain audit event.
     *
     * @param event Structured audit event record
     */
    void recordAudit(AuditEvent event);

    /**
     * Records an operational workflow event capturing job ID, correlation ID, event type, result, and safe message.
     * Guarantees that no secret or private key material is ever persisted.
     *
     * @param jobId         UUID of the deployment job
     * @param correlationId distributed trace ID
     * @param eventType     event classification (e.g. "CREDENTIAL_ACQUISITION", "MID_DISPATCH", "VERIFICATION")
     * @param result        outcome (e.g. "SUCCESS", "FAILURE", "RETRY_SCHEDULED", "ESCALATED")
     * @param safeMessage   human-readable safe message free of credentials or private keys
     */
    void recordWorkflowEvent(UUID jobId, String correlationId, String eventType, String result, String safeMessage);

    /**
     * Records an operational workflow event with contextual metadata.
     *
     * @param jobId         UUID of the deployment job
     * @param correlationId distributed trace ID
     * @param eventType     event classification
     * @param result        outcome
     * @param safeMessage   human-readable safe message
     * @param metadata      additional non-sensitive operational attributes
     */
    void recordWorkflowEvent(UUID jobId, String correlationId, String eventType, String result, String safeMessage, Map<String, Object> metadata);
}
