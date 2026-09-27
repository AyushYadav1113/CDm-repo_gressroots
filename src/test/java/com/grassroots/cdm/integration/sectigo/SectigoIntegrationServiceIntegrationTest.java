package com.grassroots.cdm.integration.sectigo;

import com.grassroots.cdm.AbstractIntegrationTest;
import com.grassroots.cdm.dto.SectigoSyncResultDto;
import com.grassroots.cdm.entity.AuditLogRecord;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.integration.sectigo.dto.SectigoCertificateDto;
import com.grassroots.cdm.integration.sectigo.exception.SectigoServerException;
import com.grassroots.cdm.repository.AuditLogRecordRepository;
import com.grassroots.cdm.repository.CertificateRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SectigoIntegrationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SectigoIntegrationService sectigoIntegrationService;

    @Autowired
    private CertificateRecordRepository certificateRecordRepository;

    @Autowired
    private AuditLogRecordRepository auditLogRecordRepository;

    @MockitoBean
    private SectigoClient sectigoClient;

    @BeforeEach
    void cleanUp() {
        certificateRecordRepository.deleteAll();
    }

    @Test
    @DisplayName("New certificate: successfully syncs and persists new Sectigo certificate into PostgreSQL")
    void syncCertificates_newCertificate_persistsInDatabase() {
        SectigoCertificateDto dto = createDto(
                "sectigo-new-001",
                "api.grassroots.internal",
                "7A:B3:CD:EF:12:34:56:78",
                "9E:8D:7C:6B:5A:4F:3E:2D:1C:0B:9A:8F:7E:6D:5C:4B:3A:2F:1E:0D:9C:8B:7A:6F:5E:4D:3C:2B:1A:0F:9E:8D",
                "Sectigo RSA Organization Validation Secure Server CA",
                "2026-01-01T00:00:00Z",
                "2028-01-01T23:59:59Z",
                List.of("api.grassroots.internal", "api-backup.grassroots.internal"),
                "ISSUED",
                null
        );

        when(sectigoClient.fetchAllCertificates(any(), any())).thenReturn(List.of(dto));

        SectigoSyncResultDto result = sectigoIntegrationService.syncCertificates("CORR-SECTIGO-NEW");

        assertThat(result.totalRetrieved()).isEqualTo(1);
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(0);
        assertThat(result.errors()).isEmpty();

        // Verify entity in PostgreSQL
        List<CertificateRecord> records = certificateRecordRepository.findAll();
        assertThat(records).hasSize(1);
        CertificateRecord record = records.get(0);
        assertThat(record.getExternalId()).isEqualTo("sectigo-new-001");
        assertThat(record.getCommonName()).isEqualTo("api.grassroots.internal");
        assertThat(record.getSource()).isEqualTo(CertificateSource.SECTIGO);
        assertThat(record.getStatus()).isEqualTo(CertificateStatus.ACTIVE);
        // Normalized hex without colons
        assertThat(record.getSerialNumber()).isEqualTo("7AB3CDEF12345678");
        assertThat(record.getThumbprint()).isEqualTo("9E8D7C6B5A4F3E2D1C0B9A8F7E6D5C4B3A2F1E0D9C8B7A6F5E4D3C2B1A0F9E8D");
        assertThat(record.getSubjectAlternativeNames()).contains("api.grassroots.internal", "api-backup.grassroots.internal");

        // Verify audit log entry
        List<AuditLogRecord> audits = auditLogRecordRepository.findByCorrelationId("CORR-SECTIGO-NEW");
        assertThat(audits).isNotEmpty();
        assertThat(audits.get(0).getOutcome()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Duplicate certificate: repeated synchronization does NOT create duplicate records (idempotent)")
    void syncCertificates_duplicateCertificate_idempotent() {
        SectigoCertificateDto dto = createDto(
                "sectigo-dup-001",
                "portal.grassroots.internal",
                "1122334455667788",
                "AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899",
                "Sectigo RSA Domain Validation Secure Server CA",
                "2026-02-01T00:00:00Z",
                "2028-02-01T23:59:59Z",
                List.of("portal.grassroots.internal"),
                "ISSUED",
                null
        );

        when(sectigoClient.fetchAllCertificates(any(), any())).thenReturn(List.of(dto));

        // First execution: creates record
        SectigoSyncResultDto firstResult = sectigoIntegrationService.syncCertificates("CORR-DUP-1");
        assertThat(firstResult.createdCount()).isEqualTo(1);
        assertThat(certificateRecordRepository.count()).isEqualTo(1);

        // Second execution: updates existing record, 0 duplicates
        SectigoSyncResultDto secondResult = sectigoIntegrationService.syncCertificates("CORR-DUP-2");
        assertThat(secondResult.createdCount()).isEqualTo(0);
        assertThat(secondResult.updatedCount()).isEqualTo(1);
        assertThat(certificateRecordRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Renewed certificate: detects renewal linkage to predecessor and publishes CERTIFICATE_RENEWED audit")
    void syncCertificates_renewedCertificate_detectsLinkageAndAudits() {
        // Pre-existing predecessor certificate discovered earlier
        CertificateRecord predecessor = new CertificateRecord();
        predecessor.setExternalId("sectigo-old-predecessor");
        predecessor.setCommonName("payments.grassroots.internal");
        predecessor.setSerialNumber("OLDSERIAL12345");
        predecessor.setThumbprint("OLDTHUMBPRINT1234567890ABCDEF1234567890ABCDEF");
        predecessor.setFingerprintSha256("OLDTHUMBPRINT1234567890ABCDEF1234567890ABCDEF");
        predecessor.setIssuer("Sectigo RSA Organization Validation Secure Server CA");
        predecessor.setValidFrom(Instant.parse("2024-01-01T00:00:00Z"));
        predecessor.setValidTo(Instant.parse("2026-01-01T23:59:59Z"));
        predecessor.setSource(CertificateSource.SECTIGO);
        predecessor.setStatus(CertificateStatus.EXPIRING);
        certificateRecordRepository.saveAndFlush(predecessor);

        // New renewed certificate retrieved from Sectigo referencing predecessor
        SectigoCertificateDto renewedDto = createDto(
                "sectigo-renewed-002",
                "payments.grassroots.internal",
                "NEWSERIAL67890",
                "NEWTHUMBPRINT1234567890ABCDEF1234567890ABCDEF",
                "Sectigo RSA Organization Validation Secure Server CA",
                "2026-01-01T00:00:00Z",
                "2028-01-01T23:59:59Z",
                List.of("payments.grassroots.internal", "pay.grassroots.internal"),
                "ISSUED",
                "sectigo-old-predecessor" // Points to predecessor
        );

        when(sectigoClient.fetchAllCertificates(any(), any())).thenReturn(List.of(renewedDto));

        SectigoSyncResultDto result = sectigoIntegrationService.syncCertificates("CORR-RENEWED-01");

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(certificateRecordRepository.count()).isEqualTo(2);

        // Verify newly created renewed certificate
        Optional<CertificateRecord> newCertOpt = certificateRecordRepository.findByExternalId("sectigo-renewed-002");
        assertThat(newCertOpt).isPresent();
        assertThat(newCertOpt.get().getStatus()).isEqualTo(CertificateStatus.ACTIVE);

        // Verify CERTIFICATE_RENEWED audit record
        List<AuditLogRecord> renewAudits = auditLogRecordRepository.findByAction("CERTIFICATE_RENEWED");
        assertThat(renewAudits).isNotEmpty();
        AuditLogRecord renewAudit = renewAudits.get(0);
        assertThat(renewAudit.getDetails()).contains("oldCertificateId");
        assertThat(renewAudit.getDetails()).contains("sectigo-old-predecessor");
    }

    @Test
    @DisplayName("Pagination: end-to-end synchronization across multiple certificate pages")
    void syncCertificates_pagination_persistsAllRecords() {
        SectigoCertificateDto cert1 = createDto("c-1", "app1.grassroots.internal", "S1", "THUMB1", "CA", "2026-01-01T00:00:00Z", "2028-01-01T00:00:00Z", List.of("app1.grassroots.internal"), "ISSUED", null);
        SectigoCertificateDto cert2 = createDto("c-2", "app2.grassroots.internal", "S2", "THUMB2", "CA", "2026-01-01T00:00:00Z", "2028-01-01T00:00:00Z", List.of("app2.grassroots.internal"), "ISSUED", null);
        SectigoCertificateDto cert3 = createDto("c-3", "app3.grassroots.internal", "S3", "THUMB3", "CA", "2026-01-01T00:00:00Z", "2028-01-01T00:00:00Z", List.of("app3.grassroots.internal"), "ISSUED", null);

        when(sectigoClient.fetchAllCertificates(any(), any())).thenReturn(List.of(cert1, cert2, cert3));

        SectigoSyncResultDto result = sectigoIntegrationService.syncCertificates("CORR-PAGE-ALL");

        assertThat(result.totalRetrieved()).isEqualTo(3);
        assertThat(result.createdCount()).isEqualTo(3);
        assertThat(certificateRecordRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("Client Failure: records failure audit entry in database when Sectigo client throws")
    void syncCertificates_clientFailure_recordsFailureAudit() {
        when(sectigoClient.fetchAllCertificates(any(), any()))
                .thenThrow(new SectigoServerException("Sectigo gateway down 503", 503));

        assertThatThrownBy(() -> sectigoIntegrationService.syncCertificates("CORR-FAIL-01"))
                .isInstanceOf(SectigoServerException.class);

        List<AuditLogRecord> audits = auditLogRecordRepository.findByCorrelationId("CORR-FAIL-01");
        assertThat(audits).isNotEmpty();
        assertThat(audits.get(0).getOutcome()).isEqualTo("FAILURE");
        assertThat(audits.get(0).getDetails()).contains("503");
    }

    private SectigoCertificateDto createDto(
            String id, String commonName, String serialNumber, String thumbprint,
            String issuer, String validFrom, String validTo, List<String> sans,
            String status, String renewedFromId
    ) {
        SectigoCertificateDto dto = new SectigoCertificateDto();
        dto.setId(id);
        dto.setCommonName(commonName);
        dto.setSerialNumber(serialNumber);
        dto.setSha256Fingerprint(thumbprint);
        dto.setIssuer(issuer);
        dto.setValidFrom(validFrom);
        dto.setValidTo(validTo);
        dto.setSubjectAlternativeNames(sans);
        dto.setStatus(status);
        dto.setRenewedFromCertificateId(renewedFromId);
        return dto;
    }
}
