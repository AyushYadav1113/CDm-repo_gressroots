package com.grassroots.cdm.discovery.mapper;

import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.integration.servicenow.model.DiscoveredCertificateItem;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Mapper component converting decoupled discovery items into CDM domain entities.
 */
@Component
public class CertificateDiscoveryMapper {

    private static final Duration EXPIRING_WINDOW = Duration.ofDays(30);

    /**
     * Converts a discovered item into a new persistent CertificateRecord.
     *
     * @param item Discovered item from ServiceNow
     * @return New CertificateRecord entity
     */
    public CertificateRecord toEntity(DiscoveredCertificateItem item) {
        if (item == null) {
            return null;
        }

        CertificateRecord record = new CertificateRecord();
        record.setExternalId(item.externalId());
        record.setCommonName(item.commonName());
        record.setSerialNumber(item.serialNumber());
        record.setThumbprint(item.thumbprint());
        record.setFingerprintSha256(item.thumbprint());
        record.setIssuer(item.issuer());
        record.setValidFrom(item.validFrom());
        record.setValidTo(item.validTo());
        record.setSubjectAlternativeNames(item.subjectAlternativeNames());
        record.setSource(CertificateSource.SERVICENOW);
        record.setStatus(resolveStatus(item.validTo(), item.rawStatus()));

        return record;
    }

    /**
     * Updates an existing CertificateRecord with refreshed discovery metadata.
     *
     * @param existing Existing CertificateRecord from database
     * @param item     Latest discovered metadata
     */
    public void updateEntity(CertificateRecord existing, DiscoveredCertificateItem item) {
        if (existing == null || item == null) {
            return;
        }

        if (item.externalId() != null && existing.getExternalId() == null) {
            existing.setExternalId(item.externalId());
        }

        if (item.commonName() != null && !item.commonName().isBlank()) {
            existing.setCommonName(item.commonName());
        }

        if (item.issuer() != null && !item.issuer().isBlank()) {
            existing.setIssuer(item.issuer());
        }

        if (item.validFrom() != null) {
            existing.setValidFrom(item.validFrom());
        }

        if (item.validTo() != null) {
            existing.setValidTo(item.validTo());
        }

        if (item.subjectAlternativeNames() != null && !item.subjectAlternativeNames().isBlank()) {
            existing.setSubjectAlternativeNames(item.subjectAlternativeNames());
        }

        // Update status based on current validity window
        existing.setStatus(resolveStatus(item.validTo(), item.rawStatus()));
    }

    /**
     * Resolves the certificate operational lifecycle status based on dates and raw status.
     */
    public CertificateStatus resolveStatus(Instant validTo, String rawStatus) {
        if (rawStatus != null && rawStatus.toUpperCase().contains("REVOKED")) {
            return CertificateStatus.REVOKED;
        }

        if (validTo != null) {
            Instant now = Instant.now();
            if (validTo.isBefore(now)) {
                return CertificateStatus.EXPIRED;
            }
            if (validTo.isBefore(now.plus(EXPIRING_WINDOW))) {
                return CertificateStatus.EXPIRING;
            }
        }

        return CertificateStatus.ACTIVE;
    }
}
