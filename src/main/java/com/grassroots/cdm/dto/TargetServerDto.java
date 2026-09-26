package com.grassroots.cdm.dto;

import java.util.UUID;

/**
 * Data transfer representation of a managed target server.
 */
public record TargetServerDto(
        UUID id,
        String hostname,
        String ipAddress,
        String operatingSystem,
        String technology,
        String environment,
        UUID midServerId,
        String midServerName,
        String status
) {}
