package com.grassroots.cdm.controller;

import com.grassroots.cdm.dto.ApiResponse;
import com.grassroots.cdm.dto.AuditLogDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.service.AuditQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * REST controller for inspecting immutable audit log events and compliance records.
 */
@RestController
@RequestMapping("/api/v1/audit-events")
@Tag(name = "Audit Events", description = "Endpoints for inspecting immutable audit events, compliance trails, and security logs")
@SecurityRequirement(name = "basicAuth")
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'ORCHESTRATOR')")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    @Operation(summary = "List audit events", description = "Retrieves a paginated list of immutable audit logs with rich filtering options")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved audit events"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getAuditEvents(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "timestamp") String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(defaultValue = "DESC") String direction,
            @Parameter(description = "Filter by correlation ID") @RequestParam(required = false) String correlationId,
            @Parameter(description = "Filter by event type") @RequestParam(required = false) String eventType,
            @Parameter(description = "Filter by action") @RequestParam(required = false) String action,
            @Parameter(description = "Filter by actor") @RequestParam(required = false) String actor,
            @Parameter(description = "Filter by entity name") @RequestParam(required = false) String entityName,
            @Parameter(description = "Filter by entity ID") @RequestParam(required = false) String entityId,
            @Parameter(description = "Filter by outcome (e.g., SUCCESS, FAILURE, MANUAL_REVIEW)") @RequestParam(required = false) String outcome,
            @Parameter(description = "Start timestamp (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "End timestamp (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        Sort sort = "ASC".equalsIgnoreCase(direction) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PagedResponse<AuditLogDto> result = auditQueryService.getAuditEvents(
                correlationId, eventType, action, actor, entityName, entityId, outcome, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success("Audit events retrieved successfully", result));
    }
}
