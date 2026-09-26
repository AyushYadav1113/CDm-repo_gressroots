package com.grassroots.cdm.dto;

import java.time.Instant;
import java.util.Map;

/**
 * System diagnostic and readiness status payload.
 */
public record SystemStatusDto(
        String applicationName,
        String version,
        String status,
        Instant serverTime,
        String javaVersion,
        Map<String, String> components
) {}
