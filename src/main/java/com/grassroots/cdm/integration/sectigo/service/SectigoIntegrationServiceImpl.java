package com.grassroots.cdm.integration.sectigo.service;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.dto.SectigoSyncResultDto;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.integration.sectigo.SectigoClient;
import com.grassroots.cdm.integration.sectigo.SectigoIntegrationService;
import com.grassroots.cdm.integration.sectigo.dto.SectigoCertificateDto;
import com.grassroots.cdm.integration.sectigo.mapper.CertificateSectigoMapper;
import com.grassroots.cdm.integration.sectigo.model.SectigoCertificateItem;
import com.grassroots.cdm.repository.CertificateRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link SectigoIntegrationService}.
 * Orchestrates retrieval from Sectigo SCM, transforms DTOs into clean models,
 * executes idempotent upserts against {@link CertificateRecordRepository},
 * tracks renewal relationships, and records structured audit events.
 */
@Service
public class SectigoIntegrationServiceImpl implements SectigoIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(SectigoIntegrationServiceImpl.class);

    private final SectigoClient sectigoClient;
    private final CertificateRecordRepository certificateRecordRepository;
    private final CertificateSectigoMapper mapper;
    private final AuditService auditService;

    public SectigoIntegrationServiceImpl(
            SectigoClient sectigoClient,
            CertificateRecordRepository certificateRecordRepository,
            CertificateSectigoMapper mapper,
            AuditService auditService
    ) {
        this.sectigoClient = sectigoClient;
        this.certificateRecordRepository = certificateRecordRepository;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    @Override
    public List<SectigoCertificateItem> fetchCertificates(String correlationId) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        List<SectigoCertificateDto> dtos = sectigoClient.fetchAllCertificates("ISSUED", resolvedCorrelationId);
        return dtos.stream()
                .map(mapper::toItem)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Optional<SectigoCertificateItem> fetchCertificateById(String certificateId, String correlationId) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        return sectigoClient.fetchCertificateById(certificateId, resolvedCorrelationId)
                .map(mapper::toItem);
    }

    @Override
    @Transactional
    public SectigoSyncResultDto syncCertificates(String correlationId) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        MDC.put("correlationId", resolvedCorrelationId);
        long start = System.currentTimeMillis();

        log.info("Initiating Sectigo certificate synchronization (correlationId={})", resolvedCorrelationId);

        List<SectigoCertificateItem> items;
        try {
            items = fetchCertificates(resolvedCorrelationId);
        } catch (Exception ex) {
            log.error("Failed to retrieve certificates from Sectigo: {}", ex.getMessage(), ex);
            String failureDetails = String.format("{\"error\":\"%s\"}",
                    ex.getMessage() != null ? ex.getMessage().replace("\"", "'") : "Unknown");
            auditService.recordAudit(AuditEvent.create(
                    AuditAction.CERTIFICATE_DISCOVERED,
                    "SectigoSCM",
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

        for (SectigoCertificateItem item : items) {
            try {
                UpsertOutcome outcome = upsertCertificate(item, resolvedCorrelationId);
                switch (outcome) {
                    case CREATED -> createdCount++;
                    case UPDATED -> updatedCount++;
                    case UNCHANGED -> unchangedCount++;
                }
            } catch (Exception ex) {
                log.error("Failed processing Sectigo certificate cn={}, certId={}: {}",
                        item.commonName(), item.certificateId(), ex.getMessage(), ex);
                errors.add(String.format("Error processing %s (ID: %s): %s",
                        item.commonName(), item.certificateId(), ex.getMessage()));
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("Sectigo certificate synchronization completed: total={}, created={}, updated={}, unchanged={}, errors={}, duration={}ms",
                items.size(), createdCount, updatedCount, unchangedCount, errors.size(), duration);

        String successDetails = String.format("{\"total\":%d,\"created\":%d,\"updated\":%d,\"unchanged\":%d,\"errors\":%d,\"durationMs\":%d}",
                items.size(), createdCount, updatedCount, unchangedCount, errors.size(), duration);

        auditService.recordAudit(AuditEvent.create(
                AuditAction.CERTIFICATE_DISCOVERED,
                "SectigoSCM",
                "SYNC-" + resolvedCorrelationId,
                "SYSTEM",
                errors.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS",
                successDetails,
                "127.0.0.1"
        ));

        return new SectigoSyncResultDto(
                resolvedCorrelationId,
                items.size(),
                createdCount,
                updatedCount,
                unchangedCount,
                duration,
                errors
        );
    }

    @Override
    @Transactional
    public SectigoSyncResultDto syncCertificateById(String certificateId, String correlationId) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        MDC.put("correlationId", resolvedCorrelationId);
        long start = System.currentTimeMillis();

        log.info("Syncing single certificate certId={} (correlationId={})", certificateId, resolvedCorrelationId);
        Optional<SectigoCertificateItem> itemOpt = fetchCertificateById(certificateId, resolvedCorrelationId);

        if (itemOpt.isEmpty()) {
            return new SectigoSyncResultDto(resolvedCorrelationId, 0, 0, 0, 0,
                    System.currentTimeMillis() - start, List.of("Certificate not found in Sectigo for ID: " + certificateId));
        }

        SectigoCertificateItem item = itemOpt.get();
        UpsertOutcome outcome = upsertCertificate(item, resolvedCorrelationId);

        int created = (outcome == UpsertOutcome.CREATED) ? 1 : 0;
        int updated = (outcome == UpsertOutcome.UPDATED) ? 1 : 0;
        int unchanged = (outcome == UpsertOutcome.UNCHANGED) ? 1 : 0;

        return new SectigoSyncResultDto(resolvedCorrelationId, 1, created, updated, unchanged,
                System.currentTimeMillis() - start, List.of());
    }

    @Override
    public byte[] downloadCertificateChain(String certificateId, String correlationId) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        return sectigoClient.downloadCertificateChain(certificateId, resolvedCorrelationId);
    }

    private UpsertOutcome upsertCertificate(SectigoCertificateItem item, String correlationId) {
        Optional<CertificateRecord> existingOpt = findExistingCertificate(item);
        CertificateRecord savedRecord;
        UpsertOutcome outcome;

        if (existingOpt.isPresent()) {
            CertificateRecord existing = existingOpt.get();
            mapper.updateEntity(existing, item);
            savedRecord = certificateRecordRepository.saveAndFlush(existing);
            outcome = UpsertOutcome.UPDATED;
            log.debug("Updated existing Sectigo certificate id={}, cn={}, thumbprint={}",
                    savedRecord.getId(), savedRecord.getCommonName(), savedRecord.getThumbprint());
        } else {
            CertificateRecord newRecord = mapper.toEntity(item);
            savedRecord = certificateRecordRepository.saveAndFlush(newRecord);
            outcome = UpsertOutcome.CREATED;
            log.info("Created new Sectigo certificate id={}, cn={}, thumbprint={}",
                    savedRecord.getId(), savedRecord.getCommonName(), savedRecord.getThumbprint());
        }

        // Detect and record renewal linkage if Sectigo indicates predecessor
        if (item.isRenewal()) {
            handleRenewalLinkage(savedRecord, item, correlationId);
        }

        return outcome;
    }

    private void handleRenewalLinkage(CertificateRecord newCert, SectigoCertificateItem item, String correlationId) {
        String predecessorId = item.renewedFromCertificateId();
        Optional<CertificateRecord> oldCertOpt = certificateRecordRepository.findByExternalId(predecessorId);

        if (oldCertOpt.isPresent()) {
            CertificateRecord oldCert = oldCertOpt.get();
            log.info("Detected certificate renewal: predecessor id={}, new id={}, cn={}",
                    oldCert.getId(), newCert.getId(), newCert.getCommonName());

            String renewalDetails = String.format(
                    "{\"oldCertificateId\":\"%s\",\"newCertificateId\":\"%s\",\"commonName\":\"%s\",\"predecessorExternalId\":\"%s\"}",
                    oldCert.getId(), newCert.getId(), newCert.getCommonName(), predecessorId);

            auditService.recordAudit(AuditEvent.create(
                    AuditAction.CERTIFICATE_RENEWED,
                    "CertificateRecord",
                    newCert.getId().toString(),
                    "SYSTEM",
                    "SUCCESS",
                    renewalDetails,
                    "127.0.0.1"
            ));
        } else {
            log.debug("Predecessor certificate externalId={} not currently in CDM repository", predecessorId);
        }
    }

    private Optional<CertificateRecord> findExistingCertificate(SectigoCertificateItem item) {
        // 1. Check by external certificateId
        if (item.certificateId() != null && !item.certificateId().isBlank()) {
            Optional<CertificateRecord> byExternalId = certificateRecordRepository.findByExternalId(item.certificateId());
            if (byExternalId.isPresent()) {
                return byExternalId;
            }
        }

        // 2. Check by thumbprint (case-insensitive)
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

    private String resolveCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        String mdcCorrelation = MDC.get("correlationId");
        if (mdcCorrelation != null && !mdcCorrelation.isBlank()) {
            return mdcCorrelation;
        }
        return UUID.randomUUID().toString();
    }

    private enum UpsertOutcome {
        CREATED,
        UPDATED,
        UNCHANGED
    }
}
