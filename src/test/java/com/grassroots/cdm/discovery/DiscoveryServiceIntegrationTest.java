package com.grassroots.cdm.discovery;

import com.grassroots.cdm.AbstractIntegrationTest;
import com.grassroots.cdm.dto.DiscoveryResultDto;
import com.grassroots.cdm.entity.AuditLogRecord;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.integration.servicenow.ServiceNowClient;
import com.grassroots.cdm.integration.servicenow.dto.ServiceNowCertificateDto;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowAuthenticationException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowParseException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowServerException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowTimeoutException;
import com.grassroots.cdm.repository.AuditLogRecordRepository;
import com.grassroots.cdm.repository.CertificateInstallationRepository;
import com.grassroots.cdm.repository.CertificateRecordRepository;
import com.grassroots.cdm.repository.TargetServerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DiscoveryServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private CertificateRecordRepository certificateRecordRepository;

    @Autowired
    private TargetServerRepository targetServerRepository;

    @Autowired
    private CertificateInstallationRepository certificateInstallationRepository;

    @Autowired
    private AuditLogRecordRepository auditLogRecordRepository;

    @MockitoBean
    private ServiceNowClient serviceNowClient;

    @BeforeEach
    void cleanUp() {
        certificateInstallationRepository.deleteAll();
        targetServerRepository.deleteAll();
        certificateRecordRepository.deleteAll();
    }

    @Test
    @DisplayName("Successful discovery: persists multiple certificates and creates server installations")
    void successfulDiscovery_persistsCertificatesAndInstallations() {
        ServiceNowCertificateDto cert1 = createDto(
                "sys-cert-01", "api.grassroots.internal", "SN-1001", "THUMB-0001",
                "CN=Grassroots CA", "2024-01-01 00:00:00", "2028-06-01 00:00:00",
                "api.grassroots.internal, api-backup.grassroots.internal",
                "api-host-01.grassroots.internal", "443", "APACHE");

        ServiceNowCertificateDto cert2 = createDto(
                "sys-cert-02", "portal.grassroots.internal", "SN-1002", "THUMB-0002",
                "CN=Grassroots CA", "2024-01-01 00:00:00", "2028-08-01 00:00:00",
                "portal.grassroots.internal",
                "web-host-02.grassroots.internal", "8443", "IIS");

        when(serviceNowClient.fetchAllCertificates(any())).thenReturn(List.of(cert1, cert2));

        DiscoveryResultDto result = discoveryService.discoverCertificates("CORR-DISC-001");

        assertThat(result.totalDiscovered()).isEqualTo(2);
        assertThat(result.createdCount()).isEqualTo(2);
        assertThat(result.updatedCount()).isEqualTo(0);
        assertThat(result.errors()).isEmpty();

        // Verify database persistence in PostgreSQL
        List<CertificateRecord> allCerts = certificateRecordRepository.findAll();
        assertThat(allCerts).hasSize(2);

        Optional<CertificateRecord> certOpt1 = certificateRecordRepository.findByThumbprint("THUMB-0001");
        assertThat(certOpt1).isPresent();
        CertificateRecord r1 = certOpt1.get();
        assertThat(r1.getCommonName()).isEqualTo("api.grassroots.internal");
        assertThat(r1.getExternalId()).isEqualTo("sys-cert-01");
        assertThat(r1.getSource()).isEqualTo(CertificateSource.SERVICENOW);
        assertThat(r1.getStatus()).isEqualTo(CertificateStatus.ACTIVE);
        assertThat(r1.getValidTo()).isNotNull();

        // Verify Target Server and Installation
        Optional<TargetServer> serverOpt = targetServerRepository.findByHostname("api-host-01.grassroots.internal");
        assertThat(serverOpt).isPresent();

        List<CertificateInstallation> installs = certificateInstallationRepository.findByCertificateId(r1.getId());
        assertThat(installs).hasSize(1);
        assertThat(installs.get(0).getPort()).isEqualTo(443);
        assertThat(installs.get(0).getServer().getId()).isEqualTo(serverOpt.get().getId());

        // Verify Audit Log entry
        List<AuditLogRecord> audits = auditLogRecordRepository.findByCorrelationId("CORR-DISC-001");
        assertThat(audits).isNotEmpty();
        assertThat(audits.get(0).getOutcome()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Duplicate discovery: running discovery repeatedly does NOT create duplicate records")
    void duplicateDiscovery_doesNotCreateDuplicates() {
        ServiceNowCertificateDto cert = createDto(
                "sys-cert-dup", "dup.grassroots.internal", "SN-DUP-01", "THUMB-DUP-01",
                "CN=Grassroots CA", "2024-01-01 00:00:00", "2028-12-31 00:00:00",
                "dup.grassroots.internal", "dup-host.grassroots.internal", "443", "NGINX");

        when(serviceNowClient.fetchAllCertificates(any())).thenReturn(List.of(cert));

        // First run: Creates record
        DiscoveryResultDto firstRun = discoveryService.discoverCertificates("CORR-RUN-1");
        assertThat(firstRun.createdCount()).isEqualTo(1);
        assertThat(firstRun.updatedCount()).isEqualTo(0);
        assertThat(certificateRecordRepository.count()).isEqualTo(1);

        // Second run: Re-discovers same certificate
        DiscoveryResultDto secondRun = discoveryService.discoverCertificates("CORR-RUN-2");
        assertThat(secondRun.totalDiscovered()).isEqualTo(1);
        assertThat(secondRun.createdCount()).isEqualTo(0);
        assertThat(secondRun.updatedCount()).isEqualTo(1);

        // Total count in database MUST remain exactly 1
        assertThat(certificateRecordRepository.count()).isEqualTo(1);
        assertThat(targetServerRepository.count()).isEqualTo(1);
        assertThat(certificateInstallationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Certificate update: refreshed validity, status, or SANs update existing database record in place")
    void certificateUpdate_updatesExistingRecordFields() {
        ServiceNowCertificateDto original = createDto(
                "sys-cert-update", "update.grassroots.internal", "SN-UPD-01", "THUMB-UPD-01",
                "CN=Grassroots CA", "2024-01-01 00:00:00", "2025-01-01 00:00:00",
                "update.grassroots.internal", "update-host.grassroots.internal", "443", "IIS");

        when(serviceNowClient.fetchAllCertificates(any())).thenReturn(List.of(original));
        discoveryService.discoverCertificates("CORR-UPD-1");

        CertificateRecord saved = certificateRecordRepository.findByThumbprint("THUMB-UPD-01").orElseThrow();
        Instant initialValidTo = saved.getValidTo();
        Long initialVersion = saved.getVersion();

        // Updated metadata from ServiceNow with extended validTo and additional SAN
        ServiceNowCertificateDto updated = createDto(
                "sys-cert-update", "update.grassroots.internal", "SN-UPD-01", "THUMB-UPD-01",
                "CN=Grassroots CA (Renewed)", "2024-01-01 00:00:00", "2027-01-01 00:00:00",
                "update.grassroots.internal, new-san.grassroots.internal",
                "update-host.grassroots.internal", "443", "IIS");

        when(serviceNowClient.fetchAllCertificates(any())).thenReturn(List.of(updated));
        DiscoveryResultDto secondResult = discoveryService.discoverCertificates("CORR-UPD-2");

        assertThat(secondResult.updatedCount()).isEqualTo(1);

        CertificateRecord refreshed = certificateRecordRepository.findByThumbprint("THUMB-UPD-01").orElseThrow();
        assertThat(refreshed.getId()).isEqualTo(saved.getId()); // Same entity ID!
        assertThat(refreshed.getValidTo()).isAfter(initialValidTo);
        assertThat(refreshed.getSubjectAlternativeNames()).contains("new-san.grassroots.internal");
        assertThat(refreshed.getIssuer()).isEqualTo("CN=Grassroots CA (Renewed)");
        assertThat(refreshed.getVersion()).isGreaterThanOrEqualTo(initialVersion);
    }

    @Test
    @DisplayName("Partial data: records missing port, issuer, or SANs are handled gracefully")
    void partialData_handledGracefully() {
        ServiceNowCertificateDto partial = new ServiceNowCertificateDto();
        partial.setSysId("sys-partial-99");
        partial.setCommonName("partial.grassroots.internal");
        partial.setSerialNumber("SN-PARTIAL-99");
        partial.setThumbprint("THUMB-PARTIAL-99");
        // issuer, valid_from, valid_to, sans, port are all omitted/null

        when(serviceNowClient.fetchAllCertificates(any())).thenReturn(List.of(partial));

        DiscoveryResultDto result = discoveryService.discoverCertificates("CORR-PARTIAL");

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();

        Optional<CertificateRecord> found = certificateRecordRepository.findByThumbprint("THUMB-PARTIAL-99");
        assertThat(found).isPresent();
        assertThat(found.get().getCommonName()).isEqualTo("partial.grassroots.internal");
        assertThat(found.get().getStatus()).isEqualTo(CertificateStatus.ACTIVE);
    }

    @Test
    @DisplayName("ServiceNow Unavailable: records failure audit log and rethrows exception")
    void serviceNowUnavailable_recordsAuditFailureAndThrows() {
        when(serviceNowClient.fetchAllCertificates(any()))
                .thenThrow(new ServiceNowServerException("503 Service Unavailable: CMDB node down", 503));

        assertThatThrownBy(() -> discoveryService.discoverCertificates("CORR-FAIL-503"))
                .isInstanceOf(ServiceNowServerException.class);

        List<AuditLogRecord> audits = auditLogRecordRepository.findByCorrelationId("CORR-FAIL-503");
        assertThat(audits).isNotEmpty();
        assertThat(audits.get(0).getOutcome()).isEqualTo("FAILURE");
    }

    @Test
    @DisplayName("ServiceNow Authentication Failure: records failure audit log and rethrows exception")
    void serviceNowAuthenticationFailure_recordsAuditFailureAndThrows() {
        when(serviceNowClient.fetchAllCertificates(any()))
                .thenThrow(new ServiceNowAuthenticationException("401 Unauthorized: Invalid API token", 401));

        assertThatThrownBy(() -> discoveryService.discoverCertificates("CORR-FAIL-401"))
                .isInstanceOf(ServiceNowAuthenticationException.class);

        List<AuditLogRecord> audits = auditLogRecordRepository.findByCorrelationId("CORR-FAIL-401");
        assertThat(audits).isNotEmpty();
        assertThat(audits.get(0).getOutcome()).isEqualTo("FAILURE");
    }

    @Test
    @DisplayName("ServiceNow Timeout: records failure audit log and rethrows exception")
    void serviceNowTimeout_recordsAuditFailureAndThrows() {
        when(serviceNowClient.fetchAllCertificates(any()))
                .thenThrow(new ServiceNowTimeoutException("Connection timed out after 5000ms"));

        assertThatThrownBy(() -> discoveryService.discoverCertificates("CORR-TIMEOUT"))
                .isInstanceOf(ServiceNowTimeoutException.class);

        List<AuditLogRecord> audits = auditLogRecordRepository.findByCorrelationId("CORR-TIMEOUT");
        assertThat(audits).isNotEmpty();
        assertThat(audits.get(0).getOutcome()).isEqualTo("FAILURE");
    }

    @Test
    @DisplayName("ServiceNow Invalid Response: records failure audit log and rethrows exception")
    void serviceNowInvalidResponse_recordsAuditFailureAndThrows() {
        when(serviceNowClient.fetchAllCertificates(any()))
                .thenThrow(new ServiceNowParseException("Malformed JSON payload"));

        assertThatThrownBy(() -> discoveryService.discoverCertificates("CORR-PARSE-ERR"))
                .isInstanceOf(ServiceNowParseException.class);

        List<AuditLogRecord> audits = auditLogRecordRepository.findByCorrelationId("CORR-PARSE-ERR");
        assertThat(audits).isNotEmpty();
        assertThat(audits.get(0).getOutcome()).isEqualTo("FAILURE");
    }

    private ServiceNowCertificateDto createDto(
            String sysId, String cn, String sn, String thumbprint, String issuer,
            String validFrom, String validTo, String sans, String targetHost, String port, String tech
    ) {
        ServiceNowCertificateDto dto = new ServiceNowCertificateDto();
        dto.setSysId(sysId);
        dto.setCommonName(cn);
        dto.setSerialNumber(sn);
        dto.setThumbprint(thumbprint);
        dto.setIssuer(issuer);
        dto.setValidFrom(validFrom);
        dto.setValidTo(validTo);
        dto.setSubjectAlternativeNames(sans);
        dto.setTargetHost(targetHost);
        dto.setPort(port);
        dto.setTechnology(tech);
        return dto;
    }
}
