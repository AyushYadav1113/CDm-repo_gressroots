package com.grassroots.cdm.dto;

import java.util.List;

/**
 * Summary DTO describing the execution outcome of a Sectigo certificate synchronization.
 */
public record SectigoSyncResultDto(
        String correlationId,
        int totalRetrieved,
        int createdCount,
        int updatedCount,
        int unchangedCount,
        long durationMs,
        List<String> errors
) {
    public static SectigoSyncResultDto empty(String correlationId) {
        return new SectigoSyncResultDto(correlationId, 0, 0, 0, 0, 0, List.of());
    }
}
