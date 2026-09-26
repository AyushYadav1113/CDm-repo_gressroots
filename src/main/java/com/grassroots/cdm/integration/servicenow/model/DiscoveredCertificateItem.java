package com.grassroots.cdm.integration.servicenow.model;

import java.time.Instant;

/**
 * Clean, decoupled model representing a discovered certificate returned by the ServiceNow integration service.
 * Isolates the domain layer from ServiceNow-specific JSON properties and Table API structures.
 */
public record DiscoveredCertificateItem(
        String externalId,
        String commonName,
        String serialNumber,
        String thumbprint,
        String issuer,
        Instant validFrom,
        Instant validTo,
        String subjectAlternativeNames,
        String targetHost,
        Integer port,
        String ipAddress,
        String technology,
        String rawStatus
) {
    public DiscoveredCertificateItem {
        if (port == null) {
            port = 443;
        }
    }
}
