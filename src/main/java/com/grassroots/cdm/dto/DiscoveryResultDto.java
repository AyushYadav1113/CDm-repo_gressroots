package com.grassroots.cdm.dto;

import java.util.List;

/**
 * Summary DTO describing the execution outcome of a certificate discovery synchronization.
 */
public record DiscoveryResultDto(
        String correlationId,
        int totalDiscovered,
        int createdCount,
        int updatedCount,
        int unchangedCount,
        long durationMs,
        List<String> errors
) {
    public static DiscoveryResultDto empty(String correlationId) {
        return new DiscoveryResultDto(correlationId, 0, 0, 0, 0, 0, List.of());
    }
}
