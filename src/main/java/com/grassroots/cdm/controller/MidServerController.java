package com.grassroots.cdm.controller;

import com.grassroots.cdm.dto.ApiResponse;
import com.grassroots.cdm.dto.MidServerDto;
import com.grassroots.cdm.dto.MidServerHealthDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.service.MidServerService;
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
 * REST controller for inspecting ServiceNow MID Server nodes and diagnostics.
 */
@RestController
@RequestMapping("/api/v1/mid-servers")
@Tag(name = "MID Servers", description = "Endpoints for inspecting ServiceNow MID Server execution agent nodes and health diagnostics")
@SecurityRequirement(name = "basicAuth")
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'ORCHESTRATOR')")
public class MidServerController {

    private final MidServerService midServerService;

    public MidServerController(MidServerService midServerService) {
        this.midServerService = midServerService;
    }

    @GetMapping
    @Operation(summary = "List MID servers", description = "Retrieves a paginated list of registered ServiceNow MID Server execution nodes")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved MID servers"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<PagedResponse<MidServerDto>>> getMidServers(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(defaultValue = "ASC") String direction,
            @Parameter(description = "Filter by MID server operational status") @RequestParam(required = false) MidServerStatus status
    ) {
        Sort sort = "DESC".equalsIgnoreCase(direction) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PagedResponse<MidServerDto> result = midServerService.getMidServers(status, pageable);
        return ResponseEntity.ok(ApiResponse.success("MID servers retrieved successfully", result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get MID server by ID", description = "Retrieves a single ServiceNow MID Server by its unique UUID identifier")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "MID Server found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "MID Server not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<MidServerDto>> getMidServerById(
            @Parameter(description = "Unique UUID of the MID server", required = true) @PathVariable UUID id
    ) {
        MidServerDto dto = midServerService.getMidServerById(id);
        return ResponseEntity.ok(ApiResponse.success("MID server retrieved successfully", dto));
    }

    @GetMapping("/{id}/health")
    @Operation(summary = "Check MID server health", description = "Performs health diagnostic inspection and returns operational status of the MID Server")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "MID Server health status retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "MID Server not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<MidServerHealthDto>> getMidServerHealth(
            @Parameter(description = "Unique UUID of the MID server", required = true) @PathVariable UUID id
    ) {
        MidServerHealthDto dto = midServerService.getMidServerHealth(id);
        return ResponseEntity.ok(ApiResponse.success("MID server health status retrieved successfully", dto));
    }
}
