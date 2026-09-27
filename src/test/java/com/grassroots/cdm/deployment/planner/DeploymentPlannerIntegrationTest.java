package com.grassroots.cdm.deployment.planner;

import com.grassroots.cdm.AbstractIntegrationTest;
import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.deployment.planner.exception.AmbiguousMatchException;
import com.grassroots.cdm.deployment.planner.model.DeploymentPlanResult;
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
import com.grassroots.cdm.entity.enums.JobPriority;
import com.grassroots.cdm.entity.enums.MatchStatus;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.repository.AuditLogRecordRepository;
import com.grassroots.cdm.repository.CertificateInstallationRepository;
import com.grassroots.cdm.repository.CertificateRecordRepository;
import com.grassroots.cdm.repository.CertificateReplacementRepository;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import com.grassroots.cdm.repository.MidServerRepository;
import com.grassroots.cdm.repository.TargetServerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("DeploymentPlanner Integration Tests (PostgreSQL)")
class DeploymentPlannerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DeploymentPlanner deploymentPlanner;

    @Autowired
    private CertificateRecordRepository certificateRecordRepository;

    @Autowired
    private CertificateReplacementRepository replacementRepository;

    @Autowired
    private TargetServerRepository targetServerRepository;

    @Autowired
    private MidServerRepository midServerRepository;

    @Autowired
    private CertificateInstallationRepository installationRepository;

    @Autowired
    private DeploymentJobRepository deploymentJobRepository;

    @Autowired
    private AuditLogRecordRepository auditLogRecordRepository;

    @BeforeEach
    void cleanUp() {
        deploymentJobRepository.deleteAll();
        installationRepository.deleteAll();
        replacementRepository.deleteAll();
        targetServerRepository.deleteAll();
        midServerRepository.deleteAll();
        certificateRecordRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-End: Plan deployment from AUTO_MATCHED replacement, persist job with priority and audit log")
    void testEndToEndDeploymentPlanning() {
        // 1. Setup MID Server
        MidServer midServer = new MidServer();
        midServer.setName("MID-E2E-WIN-01");
        midServer.setEndpoint("https://mid-e2e.internal:443/api");
        midServer.setStatus(MidServerStatus.UP);
        midServer = midServerRepository.saveAndFlush(midServer);

        // 2. Setup Target Server
        TargetServer server = new TargetServer();
        server.setHostname("iis-e2e-01.grassroots.internal");
        server.setTechnology(ServerTechnology.IIS);
        server.setOperatingSystem(ServerOperatingSystem.WINDOWS_SERVER);
        server.setEnvironment(EnvironmentType.PRODUCTION);
        server.setStatus(ServerStatus.ACTIVE);
        server.setMidServer(midServer);
        server = targetServerRepository.saveAndFlush(server);

        // 3. Setup Old Certificate
        CertificateRecord oldCert = new CertificateRecord();
        oldCert.setCommonName("e2e.grassroots.internal");
        oldCert.setSerialNumber("SERIAL-OLD-112233");
        oldCert.setThumbprint("E2E-OLD-THUMB-11112222333344445555666677778888");
        oldCert.setFingerprintSha256("E2E-OLD-THUMB-11112222333344445555666677778888");
        oldCert.setValidFrom(Instant.now().minus(300, ChronoUnit.DAYS));
        oldCert.setValidTo(Instant.now().plus(10, ChronoUnit.DAYS)); // <= 15 days -> HIGH priority
        oldCert.setStatus(CertificateStatus.ACTIVE);
        oldCert.setSource(CertificateSource.SERVICENOW);
        oldCert = certificateRecordRepository.saveAndFlush(oldCert);

        // 4. Setup New Certificate
        CertificateRecord newCert = new CertificateRecord();
        newCert.setCommonName("e2e.grassroots.internal");
        newCert.setSerialNumber("SERIAL-NEW-445566");
        newCert.setThumbprint("E2E-NEW-THUMB-99998888777766665555444433332222");
        newCert.setFingerprintSha256("E2E-NEW-THUMB-99998888777766665555444433332222");
        newCert.setValidFrom(Instant.now());
        newCert.setValidTo(Instant.now().plus(365, ChronoUnit.DAYS));
        newCert.setStatus(CertificateStatus.ACTIVE);
        newCert.setSource(CertificateSource.SECTIGO);
        newCert = certificateRecordRepository.saveAndFlush(newCert);

        // 5. Setup Installation
        CertificateInstallation installation = new CertificateInstallation();
        installation.setCertificate(oldCert);
        installation.setServer(server);
        installation.setTechnology(ServerTechnology.IIS);
        installation.setBindingInfo("E2E-IIS-Site");
        installation.setPort(443);
        installation.setStatus(InstallationStatus.INSTALLED);
        installation = installationRepository.saveAndFlush(installation);

        // 6. Setup Replacement Pair
        CertificateReplacement replacement = new CertificateReplacement();
        replacement.setOldCertificate(oldCert);
        replacement.setNewCertificate(newCert);
        replacement.setMatchingScore(0.98);
        replacement.setMatchStatus(MatchStatus.AUTO_MATCHED);
        replacement.setMatchingReasons("[]");
        replacement = replacementRepository.saveAndFlush(replacement);

        // 7. Plan deployments
        DeploymentPlanResult planResult = deploymentPlanner.planDeployments(replacement.getId());

        assertThat(planResult).isNotNull();
        assertThat(planResult.totalInstallationsEvaluated()).isEqualTo(1);
        assertThat(planResult.plannedJobs()).hasSize(1);
        assertThat(planResult.rejections()).isEmpty();

        DeploymentJob plannedJob = planResult.plannedJobs().get(0);
        assertThat(plannedJob.getId()).isNotNull();
        assertThat(plannedJob.getDeploymentType()).isEqualTo(DeploymentType.IIS);
        assertThat(plannedJob.getTargetType()).isEqualTo("IIS");
        assertThat(plannedJob.getStatus()).isEqualTo(DeploymentJobStatus.PENDING);
        assertThat(plannedJob.getPriority()).isEqualTo(JobPriority.HIGH);
        assertThat(plannedJob.getCreationReason()).contains("e2e.grassroots.internal");
        assertThat(plannedJob.getCreationReason()).contains("IIS");

        // Verify database persistence
        Optional<DeploymentJob> persistedJob = deploymentJobRepository.findById(plannedJob.getId());
        assertThat(persistedJob).isPresent();
        assertThat(persistedJob.get().getIdempotencyKey()).isEqualTo(plannedJob.getIdempotencyKey());
        assertThat(persistedJob.get().getPriority()).isEqualTo(JobPriority.HIGH);
        assertThat(persistedJob.get().getCreationReason()).isNotBlank();

        // Verify tamper-evident audit trail
        List<AuditLogRecord> auditLogs = auditLogRecordRepository.findByAction(AuditAction.DEPLOYMENT_JOB_CREATED.name());
        assertThat(auditLogs).isNotEmpty();
        AuditLogRecord audit = auditLogs.get(auditLogs.size() - 1);
        assertThat(audit.getEntityName()).isEqualTo("DeploymentJob");
        assertThat(audit.getEntityId()).isEqualTo(plannedJob.getId().toString());
        assertThat(audit.getOutcome()).isEqualTo("SUCCESS");
        assertThat(audit.getDetails()).contains(plannedJob.getJobReference());

        // 8. Prevent duplicate deployment on repeated execution
        DeploymentPlanResult secondRunResult = deploymentPlanner.planDeployments(replacement.getId());
        assertThat(secondRunResult.plannedJobs()).isEmpty();
        assertThat(secondRunResult.rejections()).hasSize(1);
        assertThat(secondRunResult.rejections().get(0).errorType()).isEqualTo("DuplicateDeploymentJobException");
    }

    @Test
    @DisplayName("Review-Required match is prevented from automated deployment planning")
    void testAmbiguousMatchPreventedFromDeployment() {
        CertificateRecord oldCert = new CertificateRecord();
        oldCert.setCommonName("ambiguous.grassroots.internal");
        oldCert.setSerialNumber("SERIAL-AMB-OLD");
        oldCert.setThumbprint("AMB-OLD-THUMB");
        oldCert.setFingerprintSha256("AMB-OLD-THUMB");
        oldCert.setValidFrom(Instant.now().minus(100, ChronoUnit.DAYS));
        oldCert.setValidTo(Instant.now().plus(20, ChronoUnit.DAYS));
        oldCert = certificateRecordRepository.saveAndFlush(oldCert);

        CertificateRecord newCert = new CertificateRecord();
        newCert.setCommonName("ambiguous.grassroots.internal");
        newCert.setSerialNumber("SERIAL-AMB-NEW");
        newCert.setThumbprint("AMB-NEW-THUMB");
        newCert.setFingerprintSha256("AMB-NEW-THUMB");
        newCert.setValidFrom(Instant.now());
        newCert.setValidTo(Instant.now().plus(365, ChronoUnit.DAYS));
        newCert = certificateRecordRepository.saveAndFlush(newCert);

        CertificateReplacement ambiguousReplacement = new CertificateReplacement();
        ambiguousReplacement.setOldCertificate(oldCert);
        ambiguousReplacement.setNewCertificate(newCert);
        ambiguousReplacement.setMatchingScore(0.72);
        ambiguousReplacement.setMatchStatus(MatchStatus.PENDING_REVIEW);
        CertificateReplacement savedReplacement = replacementRepository.saveAndFlush(ambiguousReplacement);
        UUID targetId = savedReplacement.getId();

        assertThrows(AmbiguousMatchException.class, () ->
                deploymentPlanner.planDeployments(targetId)
        );


        assertThat(deploymentJobRepository.count()).isZero();
    }
}
