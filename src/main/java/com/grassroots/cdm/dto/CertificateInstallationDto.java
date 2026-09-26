package com.grassroots.cdm.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer representation of an installed certificate on a target server and binding.
 */
public record CertificateInstallationDto(
        UUID id,
        UUID certificateId,
        String certificateCommonName,
        String certificateThumbprint,
        UUID serverId,
        String serverHostname,
        String technology,
        String bindingInfo,
        String installationPath,
        int port,
        String status,
        Instant lastVerifiedAt
) {}
