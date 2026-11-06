package com.grassroots.cdm.service;

import com.grassroots.cdm.dto.AuditLogDto;
import com.grassroots.cdm.dto.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Service interface for querying immutable audit log records.
 */
public interface AuditQueryService {

    /**
     * Retrieves paginated audit log events matching optional filter criteria.
     */
    PagedResponse<AuditLogDto> getAuditEvents(
            String correlationId,
            String eventType,
            String action,
            String actor,
            String entityName,
            String entityId,
            String outcome,
            Instant from,
            Instant to,
            Pageable pageable
    );
}
