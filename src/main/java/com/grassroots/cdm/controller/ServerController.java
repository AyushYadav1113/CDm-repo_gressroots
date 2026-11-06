package com.grassroots.cdm.controller;

import com.grassroots.cdm.dto.ApiResponse;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.dto.TargetServerDto;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.ServerStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.service.TargetServerService;
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
 * REST controller for inspecting target application and web servers.
 */
@RestController
@RequestMapping("/api/v1/servers")
@Tag(name = "Servers", description = "Endpoints for inspecting managed target servers, hosting technologies, and environments")
@SecurityRequirement(name = "basicAuth")
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'ORCHESTRATOR')")
public class ServerController {

    private final TargetServerService targetServerService;

    public ServerController(TargetServerService targetServerService) {
        this.targetServerService = targetServerService;
    }

    @GetMapping
    @Operation(summary = "List target servers", description = "Retrieves a paginated list of servers with optional environment, technology, status, and hostname filters")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved target servers"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<PagedResponse<TargetServerDto>>> getServers(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Field to sort by") @RequestParam(defaultValue = "hostname") String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(defaultValue = "ASC") String direction,
            @Parameter(description = "Filter by environment type") @RequestParam(required = false) EnvironmentType environment,
            @Parameter(description = "Filter by server technology stack") @RequestParam(required = false) ServerTechnology technology,
            @Parameter(description = "Filter by server operational status") @RequestParam(required = false) ServerStatus status,
            @Parameter(description = "Filter by hostname (case-insensitive substring)") @RequestParam(required = false) String hostname
    ) {
        Sort sort = "DESC".equalsIgnoreCase(direction) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PagedResponse<TargetServerDto> result = targetServerService.getServers(environment, technology, status, hostname, pageable);
        return ResponseEntity.ok(ApiResponse.success("Servers retrieved successfully", result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get target server by ID", description = "Retrieves a single target server by its unique UUID identifier")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Server found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Server not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ApiResponse<TargetServerDto>> getServerById(
            @Parameter(description = "Unique UUID of the target server", required = true) @PathVariable UUID id
    ) {
        TargetServerDto dto = targetServerService.getServerById(id);
        return ResponseEntity.ok(ApiResponse.success("Server retrieved successfully", dto));
    }
}
