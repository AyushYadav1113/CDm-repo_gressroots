package com.grassroots.cdm.audit;

/**
 * Service contract for publishing and persisting immutable audit log entries.
 */
public interface AuditService {

    /**
     * Records an audit event into the immutable audit trail.
     *
     * @param event Structured audit event
     */
    void recordAudit(AuditEvent event);
}
