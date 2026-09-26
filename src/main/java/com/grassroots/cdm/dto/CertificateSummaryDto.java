package com.grassroots.cdm.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Certificate boundary transfer representation.
 */
public record CertificateSummaryDto(
        UUID id,
        String serialNumber,
        String commonName,
        String subjectAlternativeNames,
        String issuer,
        String fingerprintSha256,
        Instant validFrom,
        Instant validTo,
        String source,
        String status
) {}
