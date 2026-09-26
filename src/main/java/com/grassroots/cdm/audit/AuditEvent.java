package com.grassroots.cdm.audit;

import java.time.Instant;

/**
 * Immutable audit event payload for recording compliance and security trails.
 */
public record AuditEvent(
        AuditAction action,
        String entityName,
        String entityId,
        String actor,
        String outcome,
        String details,
        String clientIp,
        Instant timestamp
) {
    public static AuditEvent create(AuditAction action, String entityName, String entityId,
                                    String actor, String outcome, String details, String clientIp) {
        return new AuditEvent(action, entityName, entityId, actor, outcome, details, clientIp, Instant.now());
    }
}
