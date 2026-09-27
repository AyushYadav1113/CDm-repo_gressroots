package com.grassroots.cdm.integration.sectigo.mapper;

import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.integration.sectigo.dto.SectigoCertificateDto;
import com.grassroots.cdm.integration.sectigo.model.SectigoCertificateItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Mapper component converting Sectigo DTOs into intermediate models and CDM domain entities.
 */
@Component
public class CertificateSectigoMapper {

    private static final Logger log = LoggerFactory.getLogger(CertificateSectigoMapper.class);
    private static final Duration EXPIRING_WINDOW = Duration.ofDays(30);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    /**
     * Maps raw Sectigo DTO into clean, normalized intermediate item.
     *
     * @param dto Raw Sectigo DTO
     * @return Normalized SectigoCertificateItem
     */
    public SectigoCertificateItem toItem(SectigoCertificateDto dto) {
        if (dto == null) {
            return null;
        }

        String normalizedThumbprint = normalizeHex(dto.getSha256Fingerprint());
        String normalizedSerial = normalizeHex(dto.getSerialNumber());
        String sansString = formatSans(dto.getSubjectAlternativeNames(), dto.getCommonName());

        return new SectigoCertificateItem(
                dto.getId(),
                dto.getCommonName(),
                sansString,
                normalizedSerial,
                normalizedThumbprint,
                dto.getIssuer(),
                parseDate(dto.getValidFrom()),
                parseDate(dto.getValidTo()),
                dto.getStatus(),
                dto.getOrderId(),
                dto.getRenewedFromCertificateId(),
                dto.getKeyAlgorithm(),
                dto.getSignatureAlgorithm()
        );
    }

    /**
     * Converts a clean intermediate item into a new persistent CertificateRecord domain entity.
     *
     * @param item Clean Sectigo item
     * @return New CertificateRecord entity
     */
    public CertificateRecord toEntity(SectigoCertificateItem item) {
        if (item == null) {
            return null;
        }

        CertificateRecord record = new CertificateRecord();
        record.setExternalId(item.certificateId());
        record.setCommonName(item.commonName() != null ? item.commonName() : "Unknown");
        record.setSerialNumber(item.serialNumber() != null ? item.serialNumber() : "UNKNOWN");
        record.setThumbprint(item.thumbprint() != null ? item.thumbprint() : "UNKNOWN");
        record.setFingerprintSha256(item.thumbprint() != null ? item.thumbprint() : "UNKNOWN");
        record.setIssuer(item.issuer());
        record.setValidFrom(item.validFrom());
        record.setValidTo(item.validTo());
        record.setSubjectAlternativeNames(item.subjectAlternativeNames());
        record.setSource(CertificateSource.SECTIGO);
        record.setStatus(resolveStatus(item.validTo(), item.rawStatus()));

        return record;
    }

    /**
     * Updates an existing CertificateRecord with refreshed Sectigo certificate metadata.
     *
     * @param existing Existing CertificateRecord from database
     * @param item     Latest Sectigo item metadata
     */
    public void updateEntity(CertificateRecord existing, SectigoCertificateItem item) {
        if (existing == null || item == null) {
            return;
        }

        if (item.certificateId() != null && existing.getExternalId() == null) {
            existing.setExternalId(item.certificateId());
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

        // Keep or refresh fingerprint if existing was partial
        if (item.thumbprint() != null && !item.thumbprint().isBlank()) {
            existing.setThumbprint(item.thumbprint());
            existing.setFingerprintSha256(item.thumbprint());
        }

        // Refresh lifecycle status
        existing.setStatus(resolveStatus(item.validTo(), item.rawStatus()));
    }

    /**
     * Resolves the operational lifecycle status based on validity dates and raw status.
     *
     * @param validTo   Expiration timestamp
     * @param rawStatus Sectigo raw status string
     * @return Corresponding CertificateStatus
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

    /**
     * Normalizes hex strings (serials, thumbprints) by stripping delimiters and uppercasing.
     */
    public String normalizeHex(String hex) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        return hex.replaceAll("[:\\s\\-]", "").toUpperCase();
    }

    private String formatSans(List<String> sans, String fallbackCn) {
        if (sans != null && !sans.isEmpty()) {
            return String.join(", ", sans);
        }
        return fallbackCn;
    }

    /**
     * Lenient parser supporting ISO-8601, yyyy-MM-dd HH:mm:ss, yyyy-MM-dd, and epoch timestamps.
     */
    public Instant parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            // ISO-8601 instant format (e.g. 2026-01-01T00:00:00Z)
            return Instant.parse(dateStr);
        } catch (Exception ignored) {
        }

        try {
            // "yyyy-MM-dd HH:mm:ss" format
            return LocalDateTime.parse(dateStr, DATE_TIME_FORMATTER).toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }

        try {
            // "yyyy-MM-dd" format
            return LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception ignored) {
        }

        try {
            // Epoch timestamp in milliseconds or seconds
            long epoch = Long.parseLong(dateStr);
            if (dateStr.length() > 10) {
                return Instant.ofEpochMilli(epoch);
            } else {
                return Instant.ofEpochSecond(epoch);
            }
        } catch (Exception ignored) {
        }

        log.warn("Unparseable certificate date string: {}", dateStr);
        return null;
    }
}
