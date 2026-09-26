package com.grassroots.cdm.dto;

import java.util.UUID;

/**
 * Data transfer representation of a ServiceNow MID Server.
 */
public record MidServerDto(
        UUID id,
        String name,
        String endpoint,
        String status,
        String networkMetadata,
        String healthInfo
) {}
