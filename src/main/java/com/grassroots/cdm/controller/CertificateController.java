package com.grassroots.cdm.controller;

import com.grassroots.cdm.dto.ApiResponse;
import com.grassroots.cdm.dto.CertificateSummaryDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.service.CertificateService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for inspecting discovered and renewed certificates.
 */
@RestController
@RequestMapping("/api/v1/certificates")
@Tag(name = "Certificates", description = "Endpoints for inspecting SSL/TLS certificates discovered from ServiceNow and synchronized from Sectigo")
@SecurityRequirement(name = "basicAuth")
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'ORCHESTRATOR')")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @GetMapping
    @Operation(summary = "List certificates", description = "Retrieves a paginated list of certificates with optional status, source, commonName, and issuer filters")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved certificates"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized: Authentication credentials required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden: Insufficient privileges")
    })
    public ResponseEntity<ApiResponse<PagedResponse<CertificateSummaryDto>>> getCertificates(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(defaultValue = "DESC") String direction,
            @Parameter(description = "Filter by certificate lifecycle status") @RequestParam(required = false) CertificateStatus status,
            @Parameter(description = "Filter by certificate discovery source") @RequestParam(required = false) CertificateSource source,
            @Parameter(description = "Filter by Common Name (case-insensitive substring)") @RequestParam(required = false) String commonName,
            @Parameter(description = "Filter by Issuer (case-insensitive substring)") @RequestParam(required = false) String issuer
    ) {
        Sort sort = "ASC".equalsIgnoreCase(direction) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PagedResponse<CertificateSummaryDto> result = certificateService.getCertificates(status, source, commonName, issuer, pageable);
        return ResponseEntity.ok(ApiResponse.success("Certificates retrieved successfully", result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get certificate by ID", description = "Retrieves a single certificate by its unique UUID identifier")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Certificate found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Certificate not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<CertificateSummaryDto>> getCertificateById(
            @Parameter(description = "Unique UUID of the certificate", required = true) @PathVariable UUID id
    ) {
        CertificateSummaryDto dto = certificateService.getCertificateById(id);
        return ResponseEntity.ok(ApiResponse.success("Certificate retrieved successfully", dto));
    }
}
