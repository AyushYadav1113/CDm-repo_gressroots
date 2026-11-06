package com.grassroots.cdm.controller;

import com.grassroots.cdm.dto.ApiResponse;
import com.grassroots.cdm.dto.CertificateReplacementDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.enums.MatchStatus;
import com.grassroots.cdm.service.CertificateReplacementService;
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
 * REST controller for inspecting certificate renewal replacements and match correlations.
 */
@RestController
@RequestMapping("/api/v1/replacements")
@Tag(name = "Replacements", description = "Endpoints for inspecting old-to-new certificate replacement correlations and match determinations")
@SecurityRequirement(name = "basicAuth")
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'ORCHESTRATOR')")
public class ReplacementController {

    private final CertificateReplacementService replacementService;

    public ReplacementController(CertificateReplacementService replacementService) {
        this.replacementService = replacementService;
    }

    @GetMapping
    @Operation(summary = "List certificate replacements", description = "Retrieves a paginated list of replacement correlations with optional matchStatus and certificate filters")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved replacements"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<PagedResponse<CertificateReplacementDto>>> getReplacements(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(defaultValue = "DESC") String direction,
            @Parameter(description = "Filter by match status") @RequestParam(required = false) MatchStatus matchStatus,
            @Parameter(description = "Filter by old certificate ID") @RequestParam(required = false) UUID oldCertificateId,
            @Parameter(description = "Filter by new certificate ID") @RequestParam(required = false) UUID newCertificateId
    ) {
        Sort sort = "ASC".equalsIgnoreCase(direction) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PagedResponse<CertificateReplacementDto> result = replacementService.getReplacements(matchStatus, oldCertificateId, newCertificateId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Replacements retrieved successfully", result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get certificate replacement by ID", description = "Retrieves a single replacement pair correlation by its unique UUID identifier")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Replacement correlation found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Replacement correlation not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<CertificateReplacementDto>> getReplacementById(
            @Parameter(description = "Unique UUID of the replacement correlation", required = true) @PathVariable UUID id
    ) {
        CertificateReplacementDto dto = replacementService.getReplacementById(id);
        return ResponseEntity.ok(ApiResponse.success("Replacement retrieved successfully", dto));
    }
}
