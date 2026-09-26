package com.grassroots.cdm.integration.servicenow.service;

import com.grassroots.cdm.integration.servicenow.ServiceNowClient;
import com.grassroots.cdm.integration.servicenow.ServiceNowIntegrationService;
import com.grassroots.cdm.integration.servicenow.dto.ServiceNowCertificateDto;
import com.grassroots.cdm.integration.servicenow.model.DiscoveredCertificateItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Production implementation of {@link ServiceNowIntegrationService}.
 * Retrieves raw ServiceNow DTOs from {@link ServiceNowClient}, validates data integrity,
 * sanitizes dates/thumbprints, and maps to clean {@link DiscoveredCertificateItem} models.
 */
@Service
public class ServiceNowIntegrationServiceImp implements ServiceNowIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ServiceNowIntegrationServiceImp.class);

    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC),
            DateTimeFormatter.ISO_INSTANT,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneOffset.UTC),
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
    };

    private final ServiceNowClient serviceNowClient;

    public ServiceNowIntegrationServiceImp(ServiceNowClient serviceNowClient) {
        this.serviceNowClient = serviceNowClient;
    }

    @Override
    public List<DiscoveredCertificateItem> fetchActiveCertificates(String correlationId) {
        log.info("Requesting all active certificates from ServiceNow (correlationId={})", correlationId);
        List<ServiceNowCertificateDto> dtos = serviceNowClient.fetchAllCertificates(correlationId);

        List<DiscoveredCertificateItem> items = new ArrayList<>();
        int filteredCount = 0;

        for (ServiceNowCertificateDto dto : dtos) {
            if (isValidRecord(dto)) {
                items.add(transformToItem(dto));
            } else {
                filteredCount++;
                log.warn("Filtered out incomplete ServiceNow certificate record: sys_id={}, cn={}",
                        dto.getSysId(), dto.getCommonName());
            }
        }

        log.info("ServiceNow discovery transformation complete: received={}, valid={}, filtered={}, correlationId={}",
                dtos.size(), items.size(), filteredCount, correlationId);
        return items;
    }

    @Override
    public Optional<DiscoveredCertificateItem> fetchCertificateBySysId(String sysId, String correlationId) {
        return serviceNowClient.fetchCertificateBySysId(sysId, correlationId)
                .filter(this::isValidRecord)
                .map(this::transformToItem);
    }

    @Override
    public void updateCertificateStatus(String sysId, String status, String correlationId) {
        serviceNowClient.updateCertificateStatus(sysId, status, correlationId);
    }

    private boolean isValidRecord(ServiceNowCertificateDto dto) {
        if (dto == null) {
            return false;
        }
        // Must at least have either a sys_id or a commonName to be identifiable
        boolean hasId = dto.getSysId() != null && !dto.getSysId().isBlank();
        boolean hasCn = dto.getCommonName() != null && !dto.getCommonName().isBlank();
        return hasId || hasCn;
    }

    private DiscoveredCertificateItem transformToItem(ServiceNowCertificateDto dto) {
        String commonName = cleanString(dto.getCommonName());
        if (commonName == null) {
            commonName = "unknown-cn-" + (dto.getSysId() != null ? dto.getSysId() : "unidentified");
        }

        String serialNumber = cleanString(dto.getSerialNumber());
        if (serialNumber == null) {
            serialNumber = "SN-" + (dto.getSysId() != null ? dto.getSysId() : "GEN-" + System.currentTimeMillis());
        }

        String thumbprint = cleanThumbprint(dto.getThumbprint());
        if (thumbprint == null) {
            thumbprint = generateDeterministicThumbprint(dto.getSysId(), serialNumber, commonName);
        }

        Instant validFrom = parseInstant(dto.getValidFrom());
        Instant validTo = parseInstant(dto.getValidTo());

        Integer port = parsePort(dto.getPort());

        return new DiscoveredCertificateItem(
                cleanString(dto.getSysId()),
                commonName,
                serialNumber,
                thumbprint,
                cleanString(dto.getIssuer()),
                validFrom,
                validTo,
                cleanString(dto.getSubjectAlternativeNames()),
                cleanString(dto.getTargetHost()),
                port,
                cleanString(dto.getIpAddress()),
                cleanString(dto.getTechnology()),
                cleanString(dto.getState() != null ? dto.getState() : dto.getOperationalStatus())
        );
    }

    private String cleanString(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String cleanThumbprint(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // Remove colons, spaces, and uppercase
        String sanitized = raw.replace(":", "").replace(" ", "").trim().toUpperCase();
        return sanitized.isEmpty() ? null : sanitized;
    }

    private Integer parsePort(String rawPort) {
        if (rawPort == null || rawPort.isBlank()) {
            return 443;
        }
        try {
            int p = Integer.parseInt(rawPort.trim());
            return (p > 0 && p <= 65535) ? p : 443;
        } catch (NumberFormatException ex) {
            return 443;
        }
    }

    private Instant parseInstant(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        String trimmed = rawDate.trim();

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                if (formatter.equals(DATE_FORMATTERS[0])) {
                    // "yyyy-MM-dd HH:mm:ss"
                    LocalDateTime ldt = LocalDateTime.parse(trimmed, formatter);
                    return ldt.toInstant(ZoneOffset.UTC);
                } else if (formatter.equals(DATE_FORMATTERS[4])) {
                    // "yyyy-MM-dd"
                    LocalDateTime ldt = LocalDateTime.parse(trimmed + " 00:00:00", DATE_FORMATTERS[0]);
                    return ldt.toInstant(ZoneOffset.UTC);
                } else {
                    return Instant.from(formatter.parse(trimmed));
                }
            } catch (DateTimeParseException ignored) {
            }
        }

        log.warn("Could not parse date '{}' into Instant with known formats", rawDate);
        return null;
    }

    private String generateDeterministicThumbprint(String sysId, String serialNumber, String commonName) {
        String seed = (sysId != null ? sysId : "") + "|" + serialNumber + "|" + commonName;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).toUpperCase();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm missing from JVM", ex);
        }
    }
}
