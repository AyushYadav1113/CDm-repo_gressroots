package com.grassroots.cdm.discovery.impl;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.discovery.DiscoveryService;
import com.grassroots.cdm.discovery.mapper.CertificateDiscoveryMapper;
import com.grassroots.cdm.dto.DiscoveryResultDto;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.InstallationStatus;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.integration.servicenow.ServiceNowIntegrationService;
import com.grassroots.cdm.integration.servicenow.model.DiscoveredCertificateItem;
import com.grassroots.cdm.repository.CertificateInstallationRepository;
import com.grassroots.cdm.repository.CertificateRecordRepository;
import com.grassroots.cdm.repository.TargetServerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DiscoveryService}.
 * Coordinates certificate discovery ingestion from ServiceNow, performs deduplication
 * and upsert into the domain repository, associates managed server installations, and publishes audit events.
 */
@Service
public class DiscoveryServiceImpl implements DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryServiceImpl.class);

    private final ServiceNowIntegrationService serviceNowIntegrationService;
    private final CertificateRecordRepository certificateRecordRepository;
    private final TargetServerRepository targetServerRepository;
    private final CertificateInstallationRepository certificateInstallationRepository;
    private final CertificateDiscoveryMapper mapper;
    private final AuditService auditService;

    public DiscoveryServiceImpl(
            ServiceNowIntegrationService serviceNowIntegrationService,
            CertificateRecordRepository certificateRecordRepository,
            TargetServerRepository targetServerRepository,
            CertificateInstallationRepository certificateInstallationRepository,
            CertificateDiscoveryMapper mapper,
            AuditService auditService
    ) {
        this.serviceNowIntegrationService = serviceNowIntegrationService;
        this.certificateRecordRepository = certificateRecordRepository;
        this.targetServerRepository = targetServerRepository;
        this.certificateInstallationRepository = certificateInstallationRepository;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public DiscoveryResultDto discoverCertificates(String correlationId) {
        String resolvedCorrelationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId : UUID.randomUUID().toString();
        MDC.put("correlationId", resolvedCorrelationId);
        long start = System.currentTimeMillis();

        log.info("Initiating certificate discovery synchronization (correlationId={})", resolvedCorrelationId);

        List<DiscoveredCertificateItem> discoveredItems;
        try {
            discoveredItems = serviceNowIntegrationService.fetchActiveCertificates(resolvedCorrelationId);
        } catch (Exception ex) {
            log.error("Failed to retrieve certificates from ServiceNow: {}", ex.getMessage(), ex);
            String failureDetails = String.format("{\"error\":\"%s\"}",
                    ex.getMessage() != null ? ex.getMessage().replace("\"", "'") : "Unknown");
            auditService.recordAudit(AuditEvent.create(
                    AuditAction.CERTIFICATE_DISCOVERED,
                    "ServiceNowCMDB",
                    "SYNC-" + resolvedCorrelationId,
                    "SYSTEM",
                    "FAILURE",
                    failureDetails,
                    "127.0.0.1"
            ));
            throw ex;
        }

        int createdCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;
        List<String> errors = new ArrayList<>();

        for (DiscoveredCertificateItem item : discoveredItems) {
            try {
                UpsertOutcome outcome = upsertCertificate(item);
                switch (outcome) {
                    case CREATED -> createdCount++;
                    case UPDATED -> updatedCount++;
                    case UNCHANGED -> unchangedCount++;
                }
            } catch (Exception ex) {
                log.error("Failed processing discovered certificate cn={}, sysId={}: {}",
                        item.commonName(), item.externalId(), ex.getMessage(), ex);
                errors.add(String.format("Error processing %s (%s): %s",
                        item.commonName(), item.externalId(), ex.getMessage()));
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("Certificate discovery completed: total={}, created={}, updated={}, unchanged={}, errors={}, duration={}ms",
                discoveredItems.size(), createdCount, updatedCount, unchangedCount, errors.size(), duration);

        String successDetails = String.format("{\"total\":%d,\"created\":%d,\"updated\":%d,\"unchanged\":%d,\"errors\":%d,\"durationMs\":%d}",
                discoveredItems.size(), createdCount, updatedCount, unchangedCount, errors.size(), duration);

        auditService.recordAudit(AuditEvent.create(
                AuditAction.CERTIFICATE_DISCOVERED,
                "ServiceNowCMDB",
                "SYNC-" + resolvedCorrelationId,
                "SYSTEM",
                errors.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS",
                successDetails,
                "127.0.0.1"
        ));

        return new DiscoveryResultDto(
                resolvedCorrelationId,
                discoveredItems.size(),
                createdCount,
                updatedCount,
                unchangedCount,
                duration,
                errors
        );
    }

    @Override
    @Transactional
    public DiscoveryResultDto syncCertificateBySysId(String sysId, String correlationId) {
        String resolvedCorrelationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId : UUID.randomUUID().toString();
        MDC.put("correlationId", resolvedCorrelationId);
        long start = System.currentTimeMillis();

        log.info("Syncing single certificate sysId={} (correlationId={})", sysId, resolvedCorrelationId);
        Optional<DiscoveredCertificateItem> itemOpt = serviceNowIntegrationService.fetchCertificateBySysId(sysId, resolvedCorrelationId);

        if (itemOpt.isEmpty()) {
            return new DiscoveryResultDto(resolvedCorrelationId, 0, 0, 0, 0, System.currentTimeMillis() - start,
                    List.of("Certificate not found in ServiceNow for sysId: " + sysId));
        }

        DiscoveredCertificateItem item = itemOpt.get();
        UpsertOutcome outcome = upsertCertificate(item);

        int created = (outcome == UpsertOutcome.CREATED) ? 1 : 0;
        int updated = (outcome == UpsertOutcome.UPDATED) ? 1 : 0;
        int unchanged = (outcome == UpsertOutcome.UNCHANGED) ? 1 : 0;

        return new DiscoveryResultDto(resolvedCorrelationId, 1, created, updated, unchanged,
                System.currentTimeMillis() - start, List.of());
    }

    private UpsertOutcome upsertCertificate(DiscoveredCertificateItem item) {
        Optional<CertificateRecord> existingOpt = findExistingCertificate(item);
        CertificateRecord savedRecord;
        UpsertOutcome outcome;

        if (existingOpt.isPresent()) {
            CertificateRecord existing = existingOpt.get();
            mapper.updateEntity(existing, item);
            savedRecord = certificateRecordRepository.saveAndFlush(existing);
            outcome = UpsertOutcome.UPDATED;
            log.debug("Updated existing certificate id={}, cn={}, thumbprint={}",
                    savedRecord.getId(), savedRecord.getCommonName(), savedRecord.getThumbprint());
        } else {
            CertificateRecord newRecord = mapper.toEntity(item);
            savedRecord = certificateRecordRepository.saveAndFlush(newRecord);
            outcome = UpsertOutcome.CREATED;
            log.info("Created new discovered certificate id={}, cn={}, thumbprint={}",
                    savedRecord.getId(), savedRecord.getCommonName(), savedRecord.getThumbprint());
        }

        // Upsert target server and installation if target host information is available
        if (item.targetHost() != null && !item.targetHost().isBlank()) {
            upsertInstallation(savedRecord, item);
        }

        return outcome;
    }

    private Optional<CertificateRecord> findExistingCertificate(DiscoveredCertificateItem item) {
        // 1. Check by external sys_id
        if (item.externalId() != null && !item.externalId().isBlank()) {
            Optional<CertificateRecord> byExternalId = certificateRecordRepository.findByExternalId(item.externalId());
            if (byExternalId.isPresent()) {
                return byExternalId;
            }
        }

        // 2. Check by thumbprint
        if (item.thumbprint() != null && !item.thumbprint().isBlank()) {
            Optional<CertificateRecord> byThumbprint = certificateRecordRepository.findByThumbprintIgnoreCase(item.thumbprint());
            if (byThumbprint.isPresent()) {
                return byThumbprint;
            }
        }

        // 3. Check by serial number and issuer
        if (item.serialNumber() != null && !item.serialNumber().isBlank()) {
            if (item.issuer() != null && !item.issuer().isBlank()) {
                Optional<CertificateRecord> bySerialAndIssuer = certificateRecordRepository
                        .findBySerialNumberAndIssuer(item.serialNumber(), item.issuer());
                if (bySerialAndIssuer.isPresent()) {
                    return bySerialAndIssuer;
                }
            } else {
                Optional<CertificateRecord> bySerial = certificateRecordRepository.findBySerialNumber(item.serialNumber());
                if (bySerial.isPresent()) {
                    return bySerial;
                }
            }
        }

        return Optional.empty();
    }

    private void upsertInstallation(CertificateRecord certificate, DiscoveredCertificateItem item) {
        try {
            TargetServer server = targetServerRepository.findByHostname(item.targetHost())
                    .orElseGet(() -> {
                        TargetServer newServer = new TargetServer();
                        newServer.setHostname(item.targetHost());
                        newServer.setIpAddress(item.ipAddress());
                        newServer.setOperatingSystem(resolveOperatingSystem(item.technology()));
                        newServer.setTechnology(resolveTechnology(item.technology()));
                        newServer.setEnvironment(EnvironmentType.PRODUCTION);
                        newServer.setStatus(ServerStatus.ACTIVE);
                        return targetServerRepository.saveAndFlush(newServer);
                    });

            int port = item.port() != null ? item.port() : 443;
            String bindingInfo = item.commonName() != null ? item.commonName() : "default";

            Optional<CertificateInstallation> existingInstall = certificateInstallationRepository
                    .findByServerIdAndPortAndBindingInfo(server.getId(), port, bindingInfo);

            if (existingInstall.isPresent()) {
                CertificateInstallation install = existingInstall.get();
                install.setCertificate(certificate);
                install.setStatus(InstallationStatus.INSTALLED);
                install.setLastVerifiedAt(Instant.now());
                certificateInstallationRepository.saveAndFlush(install);
            } else {
                CertificateInstallation newInstall = new CertificateInstallation(
                        certificate, server, server.getTechnology(), bindingInfo, port);
                newInstall.setStatus(InstallationStatus.INSTALLED);
                newInstall.setLastVerifiedAt(Instant.now());
                certificateInstallationRepository.saveAndFlush(newInstall);
            }
        } catch (Exception ex) {
            log.warn("Could not upsert installation for cert={} on host={}: {}",
                    certificate.getCommonName(), item.targetHost(), ex.getMessage());
        }
    }

    private ServerTechnology resolveTechnology(String tech) {
        if (tech == null) {
            return ServerTechnology.IIS;
        }
        String upper = tech.toUpperCase();
        if (upper.contains("APACHE")) return ServerTechnology.APACHE;
        if (upper.contains("NGINX")) return ServerTechnology.NGINX;
        if (upper.contains("TOMCAT")) return ServerTechnology.TOMCAT;
        if (upper.contains("JAVA") || upper.contains("KEYSTORE") || upper.contains("JKS")) return ServerTechnology.JAVA_KEYSTORE;
        return ServerTechnology.IIS;
    }

    private ServerOperatingSystem resolveOperatingSystem(String tech) {
        if (tech == null) {
            return ServerOperatingSystem.WINDOWS_SERVER;
        }
        String upper = tech.toUpperCase();
        if (upper.contains("APACHE") || upper.contains("NGINX")) {
            return ServerOperatingSystem.LINUX_RHEL;
        }
        return ServerOperatingSystem.WINDOWS_SERVER;
    }

    private enum UpsertOutcome {
        CREATED,
        UPDATED,
        UNCHANGED
    }
}
