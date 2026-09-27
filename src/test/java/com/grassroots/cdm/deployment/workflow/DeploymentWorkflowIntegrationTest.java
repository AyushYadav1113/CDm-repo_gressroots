package com.grassroots.cdm.deployment.workflow;

import com.grassroots.cdm.AbstractIntegrationTest;
import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.deployment.DeploymentDispatcher;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.entity.AuditLogRecord;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.entity.enums.DeploymentType;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.InstallationStatus;
import com.grassroots.cdm.entity.enums.JobPriority;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.integration.CredentialSecret;
import com.grassroots.cdm.integration.CyberArkVaultClient;
import com.grassroots.cdm.repository.AuditLogRecordRepository;
import com.grassroots.cdm.repository.CertificateInstallationRepository;
import com.grassroots.cdm.repository.CertificateRecordRepository;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import com.grassroots.cdm.repository.MidServerRepository;
import com.grassroots.cdm.repository.TargetServerRepository;
import com.grassroots.cdm.verification.EndpointVerifier;
import com.grassroots.cdm.verification.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("DeploymentWorkflow Integration Tests (PostgreSQL)")
class DeploymentWorkflowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DeploymentWorkflowService workflowService;

    @Autowired
    private DeploymentJobRepository deploymentJobRepository;

    @Autowired
    private CertificateRecordRepository certificateRecordRepository;

    @Autowired
    private TargetServerRepository targetServerRepository;

    @Autowired
    private MidServerRepository midServerRepository;

    @Autowired
    private CertificateInstallationRepository installationRepository;

    @Autowired
    private AuditLogRecordRepository auditLogRecordRepository;

    @MockBean
    private CyberArkVaultClient cyberArkVaultClient;

    @MockBean
    private DeploymentDispatcher deploymentDispatcher;

    @MockBean
    private EndpointVerifier endpointVerifier;

    @BeforeEach
    void cleanUp() {
        deploymentJobRepository.deleteAll();
        installationRepository.deleteAll();
        targetServerRepository.deleteAll();
        midServerRepository.deleteAll();
        certificateRecordRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-End: advanceJob drives DeploymentJob from PLANNED to COMPLETED in PostgreSQL")
    void testEndToEndWorkflowAdvancementInPostgres() {
        // Setup infrastructure
        MidServer midServer = midServerRepository.saveAndFlush(
                new MidServer("MID-WF-E2E-01", "https://mid.internal:443/api", MidServerStatus.UP)
        );

        TargetServer server = new TargetServer();
        server.setHostname("app-e2e.grassroots.internal");
        server.setTechnology(ServerTechnology.IIS);
        server.setOperatingSystem(ServerOperatingSystem.WINDOWS_SERVER);
        server.setEnvironment(EnvironmentType.PRODUCTION);
        server.setStatus(ServerStatus.ACTIVE);
        server.setMidServer(midServer);
        server = targetServerRepository.saveAndFlush(server);

        CertificateRecord oldCert = new CertificateRecord();
        oldCert.setCommonName("app-e2e.grassroots.internal");
        oldCert.setSerialNumber("SERIAL-WF-OLD");
        oldCert.setThumbprint("WF-OLD-THUMB");
        oldCert.setFingerprintSha256("WF-OLD-THUMB");
        oldCert.setValidFrom(Instant.now().minus(200, ChronoUnit.DAYS));
        oldCert.setValidTo(Instant.now().plus(5, ChronoUnit.DAYS));
        oldCert.setStatus(CertificateStatus.ACTIVE);
        oldCert.setSource(CertificateSource.SERVICENOW);
        oldCert = certificateRecordRepository.saveAndFlush(oldCert);

        CertificateRecord newCert = new CertificateRecord();
        newCert.setCommonName("app-e2e.grassroots.internal");
        newCert.setSerialNumber("SERIAL-WF-NEW");
        newCert.setThumbprint("WF-NEW-THUMB");
        newCert.setFingerprintSha256("WF-NEW-THUMB");
        newCert.setValidFrom(Instant.now());
        newCert.setValidTo(Instant.now().plus(365, ChronoUnit.DAYS));
        newCert.setStatus(CertificateStatus.ACTIVE);
        newCert.setSource(CertificateSource.SECTIGO);
        newCert = certificateRecordRepository.saveAndFlush(newCert);

        CertificateInstallation installation = new CertificateInstallation();
        installation.setCertificate(oldCert);
        installation.setServer(server);
        installation.setTechnology(ServerTechnology.IIS);
        installation.setBindingInfo("Default Site");
        installation.setPort(443);
        installation.setStatus(InstallationStatus.INSTALLED);
        installation = installationRepository.saveAndFlush(installation);

        DeploymentJob job = new DeploymentJob();
        job.setJobReference("JOB-WF-E2E-001");
        job.setIdempotencyKey("DEP:app-e2e.grassroots.internal:443:TEST001");
        job.setOldCertificate(oldCert);
        job.setNewCertificate(newCert);
        job.setTargetServer(server);
        job.setInstallation(installation);
        job.setTargetHost(server.getHostname());
        job.setTargetPort(443);
        job.setTargetType("IIS");
        job.setDeploymentType(DeploymentType.IIS);
        job.setStatus(DeploymentJobStatus.PLANNED);
        job.setPriority(JobPriority.CRITICAL);
        job.setMaxRetries(3);
        job = deploymentJobRepository.saveAndFlush(job);

        // Mock external collaborators
        when(cyberArkVaultClient.retrieveCredential(anyString(), anyString(), anyString()))
                .thenReturn(new CredentialSecret("cdm_svc", "secret".toCharArray(), null, server.getHostname()));
        when(deploymentDispatcher.dispatchJob(any(DeploymentJob.class))).thenReturn("MID-TASK-E2E-999");
        when(endpointVerifier.verifyEndpoint(anyString(), anyInt(), anyString()))
                .thenReturn(VerificationResult.successful(server.getHostname(), 443, "WF-NEW-THUMB", "Handshake verified"));

        String correlationId = "CORR-E2E-WORKFLOW";

        // Step 1: PLANNED -> CREDENTIALS_ACQUIRED
        DeploymentJob s1 = workflowService.advanceJob(job.getId(), correlationId);
        assertThat(s1.getStatus()).isEqualTo(DeploymentJobStatus.CREDENTIALS_ACQUIRED);

        // Step 2: CREDENTIALS_ACQUIRED -> SENT_TO_MID
        DeploymentJob s2 = workflowService.advanceJob(job.getId(), correlationId);
        assertThat(s2.getStatus()).isEqualTo(DeploymentJobStatus.SENT_TO_MID);
        assertThat(s2.getDispatchedAt()).isNotNull();

        // Step 3: SENT_TO_MID -> RUNNING
        DeploymentJob s3 = workflowService.advanceJob(job.getId(), correlationId);
        assertThat(s3.getStatus()).isEqualTo(DeploymentJobStatus.RUNNING);
        assertThat(s3.getStartedAt()).isNotNull();

        // Step 4: RUNNING -> DEPLOYED
        DeploymentJob s4 = workflowService.advanceJob(job.getId(), correlationId);
        assertThat(s4.getStatus()).isEqualTo(DeploymentJobStatus.DEPLOYED);

        // Step 5: DEPLOYED -> VERIFIED
        DeploymentJob s5 = workflowService.advanceJob(job.getId(), correlationId);
        assertThat(s5.getStatus()).isEqualTo(DeploymentJobStatus.VERIFIED);

        // Step 6: VERIFIED -> COMPLETED
        DeploymentJob s6 = workflowService.advanceJob(job.getId(), correlationId);
        assertThat(s6.getStatus()).isEqualTo(DeploymentJobStatus.COMPLETED);
        assertThat(s6.getCompletedAt()).isNotNull();

        // Verify in database
        Optional<DeploymentJob> persisted = deploymentJobRepository.findById(job.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(DeploymentJobStatus.COMPLETED);
        assertThat(persisted.get().getCompletedAt()).isNotNull();

        // Verify CertificateInstallation updated to VERIFIED
        Optional<CertificateInstallation> updatedInstall = installationRepository.findById(installation.getId());
        assertThat(updatedInstall).isPresent();
        assertThat(updatedInstall.get().getStatus()).isEqualTo(InstallationStatus.VERIFIED);
        assertThat(updatedInstall.get().getCertificate().getId()).isEqualTo(newCert.getId());

        // Verify Old Certificate updated to REPLACED
        Optional<CertificateRecord> updatedOldCert = certificateRecordRepository.findById(oldCert.getId());
        assertThat(updatedOldCert).isPresent();
        assertThat(updatedOldCert.get().getStatus()).isEqualTo(CertificateStatus.REPLACED);

        // Verify audit trail recorded in PostgreSQL
        List<AuditLogRecord> dispatchedAudits = auditLogRecordRepository.findByAction(AuditAction.DEPLOYMENT_DISPATCHED.name());
        assertThat(dispatchedAudits).isNotEmpty();

        List<AuditLogRecord> verifiedAudits = auditLogRecordRepository.findByAction(AuditAction.LIVE_ENDPOINT_VERIFIED.name());
        assertThat(verifiedAudits).isNotEmpty();
    }
}
