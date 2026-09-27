package com.grassroots.cdm.deployment.adapter.iis;

import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.adapter.DeploymentAdapterResult;
import com.grassroots.cdm.deployment.adapter.iis.exception.IisBindingUpdateException;
import com.grassroots.cdm.deployment.adapter.iis.exception.IisCertificateImportException;
import com.grassroots.cdm.deployment.adapter.iis.exception.IisPermissionException;
import com.grassroots.cdm.deployment.adapter.iis.exception.IisValidationException;
import com.grassroots.cdm.deployment.adapter.iis.exception.IisVerificationException;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.DeploymentType;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.InstallationStatus;
import com.grassroots.cdm.entity.enums.MidServerExecutionState;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.integration.midserver.MidServerClient;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobRequestDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobResponseDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerStatusQueryResponseDto;
import com.grassroots.cdm.integration.midserver.service.MidServerExecutionService;
import com.grassroots.cdm.repository.CertificateInstallationRepository;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IisDeploymentAdapterTest {

    private MidServerClient midServerClient;
    private MidServerExecutionService executionService;
    private AuditService auditService;
    private DeploymentJobRepository deploymentJobRepository;
    private CertificateInstallationRepository installationRepository;

    private IisDeploymentAdapter adapter;

    @BeforeEach
    void setUp() {
        midServerClient = mock(MidServerClient.class);
        executionService = mock(MidServerExecutionService.class);
        auditService = mock(AuditService.class);
        deploymentJobRepository = mock(DeploymentJobRepository.class);
        installationRepository = mock(CertificateInstallationRepository.class);

        adapter = new IisDeploymentAdapter(
                midServerClient,
                executionService,
                auditService,
                deploymentJobRepository,
                installationRepository
        );
    }

    private DeploymentJob createValidIisJob() {
        MidServer midServer = new MidServer("MID-WIN-01", "https://mid-win01.internal:8443", MidServerStatus.UP);
        TargetServer targetServer = new TargetServer("iis-web01.grassroots.internal", "10.0.5.20",
                ServerOperatingSystem.WINDOWS_SERVER, ServerTechnology.IIS, EnvironmentType.PRODUCTION);
        targetServer.setMidServer(midServer);

        CertificateRecord oldCert = new CertificateRecord();
        oldCert.setId(UUID.randomUUID());
        oldCert.setCommonName("portal.grassroots.internal");
        oldCert.setThumbprint("OLDTHUMBPRINT11111111111111111111111111");

        CertificateRecord newCert = new CertificateRecord();
        newCert.setId(UUID.randomUUID());
        newCert.setCommonName("portal.grassroots.internal");
        newCert.setSerialNumber("SN778899");
        newCert.setThumbprint("NEWTHUMBPRINT22222222222222222222222222");
        newCert.setValidTo(Instant.now().plus(365, ChronoUnit.DAYS));

        CertificateInstallation installation = new CertificateInstallation(
                oldCert, targetServer, ServerTechnology.IIS, "Portal Web Site", 443
        );
        installation.setId(UUID.randomUUID());
        installation.setStatus(InstallationStatus.INSTALLED);

        DeploymentJob job = new DeploymentJob();
        job.setId(UUID.randomUUID());
        job.setJobReference("JOB-IIS-001");
        job.setIdempotencyKey("IDEMP-IIS-001");
        job.setTargetServer(targetServer);
        job.setOldCertificate(oldCert);
        job.setNewCertificate(newCert);
        job.setInstallation(installation);
        job.setDeploymentType(DeploymentType.RENEWAL_REPLACEMENT);
        job.setTargetPort(443);

        return job;
    }

    @Test
    @DisplayName("Validation passes for correct Windows Server / IIS configuration")
    void validate_success() {
        DeploymentJob job = createValidIisJob();
        assertThat(adapter.supports(job)).isTrue();
        adapter.validate(job); // should not throw
    }

    @Test
    @DisplayName("Validation fails when target OS is not WINDOWS_SERVER")
    void validate_fails_whenNotWindows() {
        DeploymentJob job = createValidIisJob();
        job.getTargetServer().setOperatingSystem(ServerOperatingSystem.LINUX_RHEL);

        assertThatThrownBy(() -> adapter.validate(job))
                .isInstanceOf(IisValidationException.class)
                .hasMessageContaining("operating system must be WINDOWS_SERVER");
    }

    @Test
    @DisplayName("Validation fails when target technology is not IIS")
    void validate_fails_whenNotIis() {
        DeploymentJob job = createValidIisJob();
        job.getTargetServer().setTechnology(ServerTechnology.APACHE);

        assertThatThrownBy(() -> adapter.validate(job))
                .isInstanceOf(IisValidationException.class)
                .hasMessageContaining("technology must be IIS");
    }

    @Test
    @DisplayName("Validation fails when assigned MID Server is DOWN")
    void validate_fails_whenMidServerDown() {
        DeploymentJob job = createValidIisJob();
        job.getTargetServer().getMidServer().setStatus(MidServerStatus.DOWN);

        assertThatThrownBy(() -> adapter.validate(job))
                .isInstanceOf(IisValidationException.class)
                .hasMessageContaining("is not UP");
    }

    @Test
    @DisplayName("1. Successful deployment: all 5 steps pass and verified thumbprint returned")
    void deploy_successful() {
        DeploymentJob job = createValidIisJob();
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto dispatchResponse = new MidServerJobResponseDto(
                "MID-TASK-IIS-001", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(dispatchResponse);

        MidServerStatusQueryResponseDto statusResponse = new MidServerStatusQueryResponseDto(
                "MID-TASK-IIS-001", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.SUCCESS, 0,
                "Certificate imported into LocalMachine\\My. Granted IIS_IUSRS Read access. Binding updated. Handshake OK.",
                null, null, Instant.now().minusSeconds(10), Instant.now().minusSeconds(5), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-IIS-001"))).thenReturn(statusResponse);

        DeploymentAdapterResult result = adapter.deploy(job);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getStatus()).isEqualTo(DeploymentAdapterResult.Status.SUCCESS);
        assertThat(result.getDeployedThumbprint()).isEqualTo("NEWTHUMBPRINT22222222222222222222222222");
        assertThat(result.getVerifiedThumbprint()).isEqualTo("NEWTHUMBPRINT22222222222222222222222222");
        assertThat(result.isRollbackExecuted()).isFalse();
        assertThat(result.getStepResults()).hasSize(4);

        // Verify audit event and installation update
        verify(auditService).recordAudit(any(AuditEvent.class));
        verify(installationRepository).saveAndFlush(any(CertificateInstallation.class));
    }

    @Test
    @DisplayName("2. Certificate import failure: throws IisCertificateImportException without rollback")
    void deploy_certificateImportFailure() {
        DeploymentJob job = createValidIisJob();
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto dispatchResponse = new MidServerJobResponseDto(
                "MID-TASK-IMPORT-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(dispatchResponse);

        MidServerStatusQueryResponseDto statusResponse = new MidServerStatusQueryResponseDto(
                "MID-TASK-IMPORT-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                IisDeploymentAdapter.EXIT_IMPORT_FAILED, null, "CryptImportKey error: Corrupted PFX",
                "import failed: unable to decode private key", Instant.now(), Instant.now(), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-IMPORT-FAIL"))).thenReturn(statusResponse);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(IisCertificateImportException.class)
                .hasMessageContaining("import failed");

        // Verify no rollback dispatch occurred since binding was never touched
        verify(midServerClient, times(1)).submitJob(any(), any());
    }

    @Test
    @DisplayName("3. Permission failure: throws IisPermissionException without rollback")
    void deploy_permissionFailure() {
        DeploymentJob job = createValidIisJob();
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto dispatchResponse = new MidServerJobResponseDto(
                "MID-TASK-PERM-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(dispatchResponse);

        MidServerStatusQueryResponseDto statusResponse = new MidServerStatusQueryResponseDto(
                "MID-TASK-PERM-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                IisDeploymentAdapter.EXIT_PERMISSIONS_FAILED, null, "Access Denied modifying ACLs on MachineKeys",
                "permission failed for IIS_IUSRS", Instant.now(), Instant.now(), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-PERM-FAIL"))).thenReturn(statusResponse);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(IisPermissionException.class)
                .hasMessageContaining("permission failed");

        verify(midServerClient, times(1)).submitJob(any(), any());
    }

    @Test
    @DisplayName("4. Binding update failure: triggers automated rollback to restore old certificate")
    void deploy_bindingUpdateFailure_triggersRollback() {
        DeploymentJob job = createValidIisJob();
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        // Initial deployment submission
        MidServerJobResponseDto deployDispatch = new MidServerJobResponseDto(
                "MID-TASK-BIND-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        // Rollback submission
        MidServerJobResponseDto rollbackDispatch = new MidServerJobResponseDto(
                "MID-TASK-ROLLBACK-01", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );

        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class)))
                .thenReturn(deployDispatch)
                .thenReturn(rollbackDispatch);

        MidServerStatusQueryResponseDto deployStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-BIND-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                IisDeploymentAdapter.EXIT_BINDING_FAILED, null, "SSL Certificate binding conflict on port 443",
                "binding update failed", Instant.now(), Instant.now(), Instant.now()
        );
        MidServerStatusQueryResponseDto rollbackStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-ROLLBACK-01", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.SUCCESS,
                0, "Successfully reverted binding to OLDTHUMBPRINT11111111111111111111111111", null, null,
                Instant.now(), Instant.now(), Instant.now()
        );

        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-BIND-FAIL"))).thenReturn(deployStatus);
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-ROLLBACK-01"))).thenReturn(rollbackStatus);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(IisBindingUpdateException.class)
                .hasMessageContaining("binding update failed");

        // Verify rollback dispatch was sent
        verify(midServerClient, times(2)).submitJob(eq(endpoint), any(MidServerJobRequestDto.class));
    }

    @Test
    @DisplayName("5. Verification failure: active thumbprint mismatch triggers rollback to old certificate")
    void deploy_verificationFailure_triggersRollback() {
        DeploymentJob job = createValidIisJob();
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto deployDispatch = new MidServerJobResponseDto(
                "MID-TASK-VERIFY-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        MidServerJobResponseDto rollbackDispatch = new MidServerJobResponseDto(
                "MID-TASK-ROLLBACK-02", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );

        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class)))
                .thenReturn(deployDispatch)
                .thenReturn(rollbackDispatch);

        MidServerStatusQueryResponseDto deployStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-VERIFY-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                IisDeploymentAdapter.EXIT_VERIFICATION_FAILED, null, "Active thumbprint does not match expected",
                "verification failed", Instant.now(), Instant.now(), Instant.now()
        );
        MidServerStatusQueryResponseDto rollbackStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-ROLLBACK-02", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.SUCCESS,
                0, "Reverted binding successfully", null, null, Instant.now(), Instant.now(), Instant.now()
        );

        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-VERIFY-FAIL"))).thenReturn(deployStatus);
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-ROLLBACK-02"))).thenReturn(rollbackStatus);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(IisVerificationException.class)
                .hasMessageContaining("verification failed");

        verify(midServerClient, times(2)).submitJob(eq(endpoint), any(MidServerJobRequestDto.class));
    }

    @Test
    @DisplayName("6. Repeated / idempotent deployment skips execution when certificate is already active")
    void deploy_repeatedIdempotent_skipsExecution() {
        DeploymentJob job = createValidIisJob();
        // Set installation certificate to already be the new certificate
        job.getInstallation().setCertificate(job.getNewCertificate());
        job.getInstallation().setStatus(InstallationStatus.INSTALLED);

        DeploymentAdapterResult result = adapter.deploy(job);

        assertThat(result.isIdempotent()).isTrue();
        assertThat(result.getStatus()).isEqualTo(DeploymentAdapterResult.Status.SKIPPED_IDEMPOTENT);
        assertThat(result.getDeployedThumbprint()).isEqualTo("NEWTHUMBPRINT22222222222222222222222222");

        // Verify MID Server was NOT invoked (no unnecessary remote calls)
        verify(midServerClient, never()).submitJob(any(), any());
    }

    @Test
    @DisplayName("7. Explicit rollback restores previous certificate binding on IIS")
    void rollback_successful() {
        DeploymentJob job = createValidIisJob();
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto rollbackDispatch = new MidServerJobResponseDto(
                "MID-TASK-EXPLICIT-ROLLBACK", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(rollbackDispatch);

        MidServerStatusQueryResponseDto rollbackStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-EXPLICIT-ROLLBACK", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.SUCCESS,
                0, "Reverted to previous thumbprint OLDTHUMBPRINT11111111111111111111111111", null, null,
                Instant.now(), Instant.now(), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-EXPLICIT-ROLLBACK"))).thenReturn(rollbackStatus);

        DeploymentAdapterResult rollbackResult = adapter.rollback(job, "Manual operator request");

        assertThat(rollbackResult.getStatus()).isEqualTo(DeploymentAdapterResult.Status.ROLLED_BACK);
        assertThat(rollbackResult.isRollbackExecuted()).isTrue();
        assertThat(rollbackResult.getDeployedThumbprint()).isEqualTo("OLDTHUMBPRINT11111111111111111111111111");

        ArgumentCaptor<MidServerJobRequestDto> captor = ArgumentCaptor.forClass(MidServerJobRequestDto.class);
        verify(midServerClient).submitJob(eq(endpoint), captor.capture());

        MidServerJobRequestDto sentReq = captor.getValue();
        assertThat(sentReq.getCertificateReference().getThumbprint()).isEqualTo("OLDTHUMBPRINT11111111111111111111111111");
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("iis.operation")).isEqualTo("ROLLBACK");
    }
}
