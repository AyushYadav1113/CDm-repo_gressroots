package com.grassroots.cdm.controller;

import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.dto.ApiResponse;
import com.grassroots.cdm.dto.DeploymentJobDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.enums.DeploymentType;
import com.grassroots.cdm.service.DeploymentService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for inspecting deployment orchestration jobs, triggering manual retries, and live verification.
 */
@RestController
@RequestMapping("/api/v1/deployments")
@Tag(name = "Deployments", description = "Endpoints for inspecting deployment orchestration jobs, triggering manual retries, and live endpoint verification")
@SecurityRequirement(name = "basicAuth")
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'ORCHESTRATOR')")
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @GetMapping
    @Operation(summary = "List deployment jobs", description = "Retrieves a paginated list of deployment jobs with optional status, targetHost, and deploymentType filters")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved deployment jobs"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<PagedResponse<DeploymentJobDto>>> getDeployments(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(defaultValue = "DESC") String direction,
            @Parameter(description = "Filter by deployment job status") @RequestParam(required = false) DeploymentJobStatus status,
            @Parameter(description = "Filter by target host (case-insensitive substring)") @RequestParam(required = false) String targetHost,
            @Parameter(description = "Filter by deployment type") @RequestParam(required = false) DeploymentType deploymentType
    ) {
        Sort sort = "ASC".equalsIgnoreCase(direction) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PagedResponse<DeploymentJobDto> result = deploymentService.getDeployments(status, targetHost, deploymentType, pageable);
        return ResponseEntity.ok(ApiResponse.success("Deployments retrieved successfully", result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get deployment job by ID", description = "Retrieves a single deployment job by its unique UUID identifier")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Deployment job found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Deployment job not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<DeploymentJobDto>> getDeploymentById(
            @Parameter(description = "Unique UUID of the deployment job", required = true) @PathVariable UUID id
    ) {
        DeploymentJobDto dto = deploymentService.getDeploymentById(id);
        return ResponseEntity.ok(ApiResponse.success("Deployment retrieved successfully", dto));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry deployment job", description = "Triggers manual retry orchestration workflow for a failed or stalled deployment job")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Deployment retry triggered successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Deployment job not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<DeploymentJobDto>> retryDeployment(
            @Parameter(description = "Unique UUID of the deployment job", required = true) @PathVariable UUID id,
            @Parameter(description = "Optional correlation ID for tracing") @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId
    ) {
        DeploymentJobDto dto = deploymentService.retryDeployment(id, correlationId);
        return ResponseEntity.ok(ApiResponse.success("Deployment retry triggered successfully", dto));
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Verify deployed certificate", description = "Triggers live TLS endpoint handshake verification on the target host")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Deployment verified successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Deployment job not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<DeploymentJobDto>> verifyDeployment(
            @Parameter(description = "Unique UUID of the deployment job", required = true) @PathVariable UUID id,
            @Parameter(description = "Optional correlation ID for tracing") @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId
    ) {
        DeploymentJobDto dto = deploymentService.verifyDeployment(id, correlationId);
        return ResponseEntity.ok(ApiResponse.success("Deployment verified successfully", dto));
    }
}
