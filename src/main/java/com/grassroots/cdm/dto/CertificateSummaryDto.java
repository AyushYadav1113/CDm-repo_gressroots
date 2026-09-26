package com.grassroots.cdm.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Certificate boundary transfer representation.
 */
public record CertificateSummaryDto(
        UUID id,
        String externalId,
        String serialNumber,
        String thumbprint,
        String commonName,
        String subjectAlternativeNames,
        String issuer,
        Instant validFrom,
        Instant validTo,
        String source,
        String status
) {}
