package com.grassroots.cdm.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer representation of an audit event record.
 */
public record AuditLogDto(
        UUID id,
        String correlationId,
        String referenceId,
        String eventType,
        String message,
        String entityName,
        String entityId,
        String actor,
        String outcome,
        String details,
        String clientIp,
        Instant timestamp
) {}
