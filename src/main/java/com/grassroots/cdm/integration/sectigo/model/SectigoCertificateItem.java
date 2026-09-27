package com.grassroots.cdm.integration.sectigo.model;

import java.time.Instant;

/**
 * Clean, decoupled model representing a certificate retrieved from Sectigo Certificate Manager.
 * Completely isolates the CDM domain model from Sectigo-specific HTTP transport and DTO schemas.
 */
public record SectigoCertificateItem(
        String certificateId,
        String commonName,
        String subjectAlternativeNames,
        String serialNumber,
        String thumbprint,
        String issuer,
        Instant validFrom,
        Instant validTo,
        String rawStatus,
        String orderId,
        String renewedFromCertificateId,
        String keyAlgorithm,
        String signatureAlgorithm
) {
    public boolean isRenewal() {
        return renewedFromCertificateId != null && !renewedFromCertificateId.isBlank();
    }
}
