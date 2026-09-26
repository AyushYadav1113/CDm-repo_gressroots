package com.grassroots.cdm.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer representation of an old-to-new certificate replacement match.
 */
public record CertificateReplacementDto(
        UUID id,
        UUID oldCertificateId,
        String oldCommonName,
        String oldThumbprint,
        UUID newCertificateId,
        String newCommonName,
        String newThumbprint,
        double matchingScore,
        String matchingReasons,
        String matchStatus,
        Instant createdAt
) {}
