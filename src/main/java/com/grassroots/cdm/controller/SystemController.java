package com.grassroots.cdm.controller;

import com.grassroots.cdm.dto.ApiResponse;
import com.grassroots.cdm.dto.SystemStatusDto;
import com.grassroots.cdm.service.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing system status, readiness, and connectivity diagnostics.
 */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System Diagnostics", description = "Endpoints for platform health, database connectivity, and readiness verification")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping("/status")
    @Operation(summary = "Get system status and component health", description = "Inspects database connectivity and orchestration readiness")
    public ResponseEntity<ApiResponse<SystemStatusDto>> getStatus() {
        SystemStatusDto status = systemService.getSystemStatus();
        return ResponseEntity.ok(ApiResponse.success("System status retrieved successfully", status));
    }

    @GetMapping("/ping")
    @Operation(summary = "Lightweight ping endpoint", description = "Returns simple pong message for liveness probes")
    public ResponseEntity<ApiResponse<String>> ping() {
        return ResponseEntity.ok(ApiResponse.success("Ping successful", systemService.ping()));
    }
}
