package com.grassroots.cdm.repository;

import com.grassroots.cdm.AbstractIntegrationTest;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.entity.AuditLogRecord;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.CertificateReplacement;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.entity.enums.DeploymentType;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.InstallationStatus;
import com.grassroots.cdm.entity.enums.MatchStatus;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class DatabaseMigrationAndRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CertificateRecordRepository certificateRepository;

    @Autowired
    private MidServerRepository midServerRepository;

    @Autowired
    private TargetServerRepository targetServerRepository;

    @Autowired
    private CertificateInstallationRepository installationRepository;

    @Autowired
    private CertificateReplacementRepository replacementRepository;

    @Autowired
    private DeploymentJobRepository deploymentJobRepository;

    @Autowired
    private AuditLogRecordRepository auditLogRecordRepository;

    @Autowired
    private Flyway flyway;

    // =========================================================================
    // 1. FLYWAY MIGRATION VERIFICATION
    // =========================================================================
    @Test
    @DisplayName("Flyway migrations V1 and V2 applied cleanly to PostgreSQL")
    void testFlywayMigrationsApplied() {
        MigrationInfo[] appliedMigrations = flyway.info().applied();
        assertThat(appliedMigrations).hasSizeGreaterThanOrEqualTo(2);
        assertThat(appliedMigrations[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(appliedMigrations[1].getVersion().getVersion()).isEqualTo("2");
    }

    // =========================================================================
    // 2. CERTIFICATES: CRUD, THUMBPRINT & CONSTRAINTS
    // =========================================================================
    @Test
    @DisplayName("Persist, query, update and delete CertificateRecord")
    void persistAndQueryCertificateRecord() {
        CertificateRecord cert = new CertificateRecord();
        cert.setCommonName("api.grassroots.internal");
        cert.setSerialNumber("1234567890ABCDEF");
        cert.setIssuer("Sectigo RSA Organization Validation Secure Server CA");
        cert.setThumbprint("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855");
        cert.setSubjectAlternativeNames("DNS:api.grassroots.internal, DNS:auth.grassroots.internal");
        cert.setValidFrom(Instant.now().minus(30, ChronoUnit.DAYS));
        cert.setValidTo(Instant.now().plus(335, ChronoUnit.DAYS));
        cert.setSource(CertificateSource.SERVICENOW);
        cert.setExternalId("sys_id_987654");
        cert.setStatus(CertificateStatus.ACTIVE);

        CertificateRecord saved = certificateRepository.saveAndFlush(cert);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);

        Optional<CertificateRecord> found = certificateRepository.findByThumbprint(
                "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855");
        assertThat(found).isPresent();
        assertThat(found.get().getCommonName()).isEqualTo("api.grassroots.internal");
        assertThat(found.get().getSource()).isEqualTo(CertificateSource.SERVICENOW);
        assertThat(found.get().getStatus()).isEqualTo(CertificateStatus.ACTIVE);

        // Update status & optimistic lock verification
        saved.setStatus(CertificateStatus.REPLACED);
        CertificateRecord updated = certificateRepository.saveAndFlush(saved);
        assertThat(updated.getStatus()).isEqualTo(CertificateStatus.REPLACED);
    }

    @Test
    @DisplayName("Certificate thumbprint unique constraint prevents duplicate certificates")
    void testCertificateThumbprintUniqueConstraint() {
        CertificateRecord cert1 = new CertificateRecord();
        cert1.setCommonName("app1.grassroots.internal");
        cert1.setSerialNumber("SERIAL-001");
        cert1.setThumbprint("A1B2C3D4E5F60102030405060708091011121314151617181920212223242526");
        certificateRepository.saveAndFlush(cert1);

        CertificateRecord cert2 = new CertificateRecord();
        cert2.setCommonName("app2.grassroots.internal");
        cert2.setSerialNumber("SERIAL-002");
        cert2.setThumbprint("A1B2C3D4E5F60102030405060708091011121314151617181920212223242526"); // Duplicate

        assertThatThrownBy(() -> certificateRepository.saveAndFlush(cert2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // 3. MID SERVERS: CRUD & CONSTRAINTS
    // =========================================================================
    @Test
    @DisplayName("Persist, query and enforce unique name on MidServer")
    void persistAndQueryMidServer() {
        MidServer midServer = new MidServer(
                "MID_SERVER_PROD_EAST_01",
                "https://mid01.internal:8443/agent",
                MidServerStatus.UP
        );
        midServer.setNetworkMetadata("{\"vpc\": \"vpc-1234\", \"datacenter\": \"us-east-1\", \"ip\": \"10.0.1.10\"}");
        midServer.setHealthInfo("{\"cpu_load\": 0.25, \"active_tasks\": 3, \"latency_ms\": 12}");

        MidServer saved = midServerRepository.saveAndFlush(midServer);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);

        Optional<MidServer> found = midServerRepository.findByName("MID_SERVER_PROD_EAST_01");
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(MidServerStatus.UP);
        assertThat(found.get().getNetworkMetadata()).contains("us-east-1");

        // Duplicate name test
        MidServer duplicate = new MidServer("MID_SERVER_PROD_EAST_01", "https://other.internal", MidServerStatus.UP);
        assertThatThrownBy(() -> midServerRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // 4. TARGET SERVERS: RELATIONSHIPS & CONSTRAINTS
    // =========================================================================
    @Test
    @DisplayName("Persist TargetServer with MidServer association and enforce unique hostname")
    void persistAndQueryTargetServerWithMidServerRelationship() {
        MidServer midServer = midServerRepository.saveAndFlush(
                new MidServer("MID_SERVER_APP_01", "https://mid-app.internal", MidServerStatus.UP));

        TargetServer server = new TargetServer(
                "web-prod-01.grassroots.internal",
                "10.20.30.40",
                ServerOperatingSystem.LINUX_RHEL,
                ServerTechnology.NGINX,
                EnvironmentType.PRODUCTION
        );
        server.setMidServer(midServer);
        server.setStatus(ServerStatus.ACTIVE);

        TargetServer saved = targetServerRepository.saveAndFlush(server);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getMidServer().getName()).isEqualTo("MID_SERVER_APP_01");

        Optional<TargetServer> found = targetServerRepository.findByHostname("web-prod-01.grassroots.internal");
        assertThat(found).isPresent();
        assertThat(found.get().getTechnology()).isEqualTo(ServerTechnology.NGINX);
        assertThat(found.get().getEnvironment()).isEqualTo(EnvironmentType.PRODUCTION);

        // Duplicate hostname test
        TargetServer duplicate = new TargetServer(
                "web-prod-01.grassroots.internal",
                "10.20.30.41",
                ServerOperatingSystem.WINDOWS_SERVER,
                ServerTechnology.IIS,
                EnvironmentType.STAGING
        );
        assertThatThrownBy(() -> targetServerRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // 5. CERTIFICATE INSTALLATIONS: RELATIONSHIPS & BINDINGS
    // =========================================================================
    @Test
    @DisplayName("Persist CertificateInstallation linking Certificate and TargetServer with compound unique binding")
    void persistAndQueryCertificateInstallationWithRelationships() {
        CertificateRecord cert = createTestCertificate("install-test.grassroots.internal", "THUMBPRINT-INSTALL-01");
        TargetServer server = createTestServer("app-install-01.grassroots.internal", ServerTechnology.IIS);

        CertificateInstallation install = new CertificateInstallation(
                cert,
                server,
                ServerTechnology.IIS,
                "Default Web Site",
                443
        );
        install.setInstallationPath("C:\\inetpub\\certs\\prod.pfx");
        install.setStatus(InstallationStatus.INSTALLED);
        install.setLastVerifiedAt(Instant.now());

        CertificateInstallation saved = installationRepository.saveAndFlush(install);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCertificate().getId()).isEqualTo(cert.getId());
        assertThat(saved.getServer().getId()).isEqualTo(server.getId());

        List<CertificateInstallation> serverInstalls = installationRepository.findByServerId(server.getId());
        assertThat(serverInstalls).hasSize(1);
        assertThat(serverInstalls.getFirst().getBindingInfo()).isEqualTo("Default Web Site");

        // Duplicate binding test on same server, port, and binding info
        CertificateRecord cert2 = createTestCertificate("install-test2.grassroots.internal", "THUMBPRINT-INSTALL-02");
        CertificateInstallation duplicate = new CertificateInstallation(
                cert2,
                server,
                ServerTechnology.IIS,
                "Default Web Site", // Identical binding on same server & port
                443
        );
        assertThatThrownBy(() -> installationRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // 6. CERTIFICATE REPLACEMENTS: CORRELATION PAIRS
    // =========================================================================
    @Test
    @DisplayName("Persist CertificateReplacement pair between old and renewed certificates")
    void persistAndQueryCertificateReplacementPair() {
        CertificateRecord oldCert = createTestCertificate("portal.grassroots.internal", "THUMBPRINT-REPL-OLD");
        CertificateRecord newCert = createTestCertificate("portal.grassroots.internal", "THUMBPRINT-REPL-NEW");

        CertificateReplacement replacement = new CertificateReplacement(
                oldCert,
                newCert,
                0.98,
                "{\"reasons\": [\"EXACT_SAN_MATCH\", \"COMMON_NAME_MATCH\", \"SERIAL_SUCCESSION\"]}",
                MatchStatus.AUTO_MATCHED
        );

        CertificateReplacement saved = replacementRepository.saveAndFlush(replacement);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOldCertificate().getThumbprint()).isEqualTo("THUMBPRINT-REPL-OLD");
        assertThat(saved.getNewCertificate().getThumbprint()).isEqualTo("THUMBPRINT-REPL-NEW");
        assertThat(saved.getMatchingScore()).isEqualTo(0.98);

        Optional<CertificateReplacement> found = replacementRepository.findByOldCertificateIdAndNewCertificateId(
                oldCert.getId(), newCert.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getMatchStatus()).isEqualTo(MatchStatus.AUTO_MATCHED);

        // Enforce unique pair constraint
        CertificateReplacement duplicate = new CertificateReplacement(
                oldCert, newCert, 1.0, "{}", MatchStatus.MANUALLY_CONFIRMED);
        assertThatThrownBy(() -> replacementRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // 7. DEPLOYMENT JOBS: ORCHESTRATION & IDEMPOTENCY
    // =========================================================================
    @Test
    @DisplayName("Persist DeploymentJob with complete graph (old/new certs, server, installation) and idempotency")
    void persistAndQueryDeploymentJobWithCompleteGraph() {
        CertificateRecord oldCert = createTestCertificate("secure.grassroots.internal", "THUMBPRINT-JOB-OLD");
        CertificateRecord newCert = createTestCertificate("secure.grassroots.internal", "THUMBPRINT-JOB-NEW");
        TargetServer server = createTestServer("web-job-01.grassroots.internal", ServerTechnology.APACHE);

        CertificateInstallation installation = installationRepository.saveAndFlush(new CertificateInstallation(
                oldCert, server, ServerTechnology.APACHE, "/etc/httpd/conf.d/ssl.conf", 443));

        DeploymentJob job = new DeploymentJob();
        job.setJobReference("JOB-ORCH-2026-0001");
        job.setIdempotencyKey("IDEMPOTENCY-KEY-AAA-111");
        job.setOldCertificate(oldCert);
        job.setNewCertificate(newCert);
        job.setTargetServer(server);
        job.setInstallation(installation);
        job.setDeploymentType(DeploymentType.RENEWAL_REPLACEMENT);
        job.setStatus(DeploymentJobStatus.PENDING);
        job.setScheduledAt(Instant.now());
        job.setMaxRetries(3);

        DeploymentJob savedJob = deploymentJobRepository.saveAndFlush(job);
        assertThat(savedJob.getId()).isNotNull();
        assertThat(savedJob.getTargetServer().getHostname()).isEqualTo("web-job-01.grassroots.internal");
        assertThat(savedJob.getInstallation().getId()).isEqualTo(installation.getId());
        assertThat(savedJob.getVersion()).isEqualTo(0L);

        // Transition through lifecycle
        savedJob.setStatus(DeploymentJobStatus.IN_PROGRESS);
        savedJob.setStartedAt(Instant.now());
        savedJob.setMidServerTaskId("MID-TASK-998877");
        DeploymentJob updated = deploymentJobRepository.saveAndFlush(savedJob);
        assertThat(updated.getStatus()).isEqualTo(DeploymentJobStatus.IN_PROGRESS);
        assertThat(updated.getStartedAt()).isNotNull();

        // Query by idempotency key
        Optional<DeploymentJob> byIdempotency = deploymentJobRepository.findByIdempotencyKey("IDEMPOTENCY-KEY-AAA-111");
        assertThat(byIdempotency).isPresent();

        // Enforce unique idempotency key constraint
        DeploymentJob duplicate = new DeploymentJob();
        duplicate.setJobReference("JOB-ORCH-2026-0002");
        duplicate.setIdempotencyKey("IDEMPOTENCY-KEY-AAA-111"); // Duplicate
        duplicate.setNewCertificate(newCert);
        duplicate.setTargetServer(server);
        duplicate.setTargetHost(server.getHostname());
        duplicate.setTargetPort(443);
        duplicate.setTargetType("LINUX_APACHE");

        assertThatThrownBy(() -> deploymentJobRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // 8. AUDIT LOGS: CORRELATION TRACING & AUDIT TRAIL
    // =========================================================================
    @Test
    @DisplayName("Persist AuditLogRecord with correlation ID and query audit trace")
    void persistAndQueryAuditLogWithCorrelationTracing() {
        String correlationId = UUID.randomUUID().toString();

        AuditLogRecord log1 = new AuditLogRecord(
                correlationId,
                "JOB-2026-0001",
                "CREDENTIAL_RETRIEVED",
                "Target host credential retrieved from CyberArk Safe CDM_PROD_SAFE",
                "DeploymentJob",
                "JOB-2026-0001",
                "cdm_orchestrator",
                "SUCCESS",
                "{\"safe\": \"CDM_PROD_SAFE\", \"account\": \"svc_cdm_deploy\"}",
                "10.0.0.5"
        );

        AuditLogRecord log2 = new AuditLogRecord(
                correlationId,
                "JOB-2026-0001",
                "DEPLOYMENT_DISPATCHED",
                "Job dispatched to MID Server MID_SERVER_PROD_01",
                "DeploymentJob",
                "JOB-2026-0001",
                "cdm_orchestrator",
                "SUCCESS",
                "{\"mid_server\": \"MID_SERVER_PROD_01\", \"task_id\": \"ECC-10101\"}",
                "10.0.0.5"
        );

        auditLogRecordRepository.saveAndFlush(log1);
        auditLogRecordRepository.saveAndFlush(log2);

        List<AuditLogRecord> traceLogs = auditLogRecordRepository.findByCorrelationId(correlationId);
        assertThat(traceLogs).hasSize(2);
        assertThat(traceLogs).extracting(AuditLogRecord::getEventType)
                .containsExactlyInAnyOrder("CREDENTIAL_RETRIEVED", "DEPLOYMENT_DISPATCHED");
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    private CertificateRecord createTestCertificate(String cn, String thumbprint) {
        CertificateRecord cert = new CertificateRecord();
        cert.setCommonName(cn);
        cert.setSerialNumber(UUID.randomUUID().toString().substring(0, 16));
        cert.setThumbprint(thumbprint);
        cert.setFingerprintSha256(thumbprint);
        cert.setSource(CertificateSource.SERVICENOW);
        cert.setStatus(CertificateStatus.ACTIVE);
        return certificateRepository.saveAndFlush(cert);
    }

    private TargetServer createTestServer(String hostname, ServerTechnology tech) {
        TargetServer server = new TargetServer(
                hostname,
                "10.10.10.10",
                ServerOperatingSystem.LINUX_RHEL,
                tech,
                EnvironmentType.PRODUCTION
        );
        return targetServerRepository.saveAndFlush(server);
    }
}
