package com.grassroots.cdm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Health and connectivity diagnostic representation for a ServiceNow MID Server node.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MidServerHealthDto(
        UUID id,
        String name,
        String endpoint,
        String status,
        boolean healthy,
        String healthInfo,
        String networkMetadata,
        Instant checkedAt
) {}
