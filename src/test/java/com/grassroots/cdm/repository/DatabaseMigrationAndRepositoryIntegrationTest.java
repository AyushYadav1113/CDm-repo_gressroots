package com.grassroots.cdm.repository;

import com.grassroots.cdm.AbstractIntegrationTest;
import com.grassroots.cdm.entity.AuditLogRecord;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class DatabaseMigrationAndRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CertificateRecordRepository certificateRepository;

    @Autowired
    private DeploymentJobRepository deploymentJobRepository;

    @Autowired
    private AuditLogRecordRepository auditLogRecordRepository;

    @Test
    @DisplayName("Persist and query CertificateRecord verifying Flyway schema and JPA auditing")
    void persistAndQueryCertificateRecord() {
        CertificateRecord cert = new CertificateRecord();
        cert.setCommonName("api.grassroots.internal");
        cert.setSerialNumber("1234567890ABCDEF");
        cert.setIssuer("Sectigo RSA Organization Validation Secure Server CA");
        cert.setFingerprintSha256("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855");
        cert.setSubjectAlternativeNames("DNS:api.grassroots.internal, DNS:auth.grassroots.internal");
        cert.setValidFrom(Instant.now().minus(30, ChronoUnit.DAYS));
        cert.setValidTo(Instant.now().plus(335, ChronoUnit.DAYS));
        cert.setSource("SERVICENOW");
        cert.setExternalId("sys_id_987654");
        cert.setStatus("ACTIVE");

        CertificateRecord saved = certificateRepository.saveAndFlush(cert);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);

        Optional<CertificateRecord> found = certificateRepository.findByFingerprintSha256(
                "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855");
        assertThat(found).isPresent();
        assertThat(found.get().getCommonName()).isEqualTo("api.grassroots.internal");
    }

    @Test
    @DisplayName("Persist DeploymentJob linked to CertificateRecord and query by job reference")
    void persistAndQueryDeploymentJob() {
        CertificateRecord oldCert = new CertificateRecord();
        oldCert.setCommonName("app.grassroots.internal");
        oldCert.setSerialNumber("OLD-SERIAL-111");
        oldCert.setFingerprintSha256("1111111111111111111111111111111111111111111111111111111111111111");
        oldCert.setSource("SERVICENOW");
        CertificateRecord savedOldCert = certificateRepository.saveAndFlush(oldCert);

        CertificateRecord newCert = new CertificateRecord();
        newCert.setCommonName("app.grassroots.internal");
        newCert.setSerialNumber("NEW-SERIAL-222");
        newCert.setFingerprintSha256("2222222222222222222222222222222222222222222222222222222222222222");
        newCert.setSource("SECTIGO");
        CertificateRecord savedNewCert = certificateRepository.saveAndFlush(newCert);

        DeploymentJob job = new DeploymentJob();
        job.setJobReference("JOB-2026-0001");
        job.setOldCertificate(savedOldCert);
        job.setNewCertificate(savedNewCert);
        job.setTargetHost("app-prod-01.grassroots.internal");
        job.setTargetPort(443);
        job.setTargetType("LINUX_NGINX");
        job.setStatus("PENDING");
        job.setMaxRetries(3);

        DeploymentJob savedJob = deploymentJobRepository.saveAndFlush(job);

        assertThat(savedJob.getId()).isNotNull();
        assertThat(savedJob.getCreatedAt()).isNotNull();

        Optional<DeploymentJob> foundJob = deploymentJobRepository.findByJobReference("JOB-2026-0001");
        assertThat(foundJob).isPresent();
        assertThat(foundJob.get().getTargetHost()).isEqualTo("app-prod-01.grassroots.internal");
        assertThat(foundJob.get().getOldCertificate().getId()).isEqualTo(savedOldCert.getId());
        assertThat(foundJob.get().getNewCertificate().getId()).isEqualTo(savedNewCert.getId());
    }

    @Test
    @DisplayName("Persist and query immutable AuditLogRecord")
    void persistAndQueryAuditLog() {
        AuditLogRecord audit = new AuditLogRecord(
                "CERTIFICATE_MATCHED",
                "DeploymentJob",
                "JOB-2026-0001",
                "system-orchestrator",
                "SUCCESS",
                "{\"strategy\": \"EXACT_SAN_MATCH\", \"confidence\": 1.0}",
                "10.0.0.1"
        );

        AuditLogRecord saved = auditLogRecordRepository.saveAndFlush(audit);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTimestamp()).isNotNull();

        List<AuditLogRecord> logs = auditLogRecordRepository.findByAction("CERTIFICATE_MATCHED");
        assertThat(logs).isNotEmpty();
        assertThat(logs.getFirst().getActor()).isEqualTo("system-orchestrator");
    }
}
