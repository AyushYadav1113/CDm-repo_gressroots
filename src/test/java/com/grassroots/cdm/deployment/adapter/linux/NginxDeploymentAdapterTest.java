package com.grassroots.cdm.deployment.adapter.linux;

import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.TargetType;
import com.grassroots.cdm.deployment.adapter.DeploymentAdapterResult;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxCertificateTransferException;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxConfigurationValidationException;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxPermissionException;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxPrivateKeyTransferException;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxServiceReloadException;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxValidationException;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxVerificationException;
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

class NginxDeploymentAdapterTest {

    private MidServerClient midServerClient;
    private MidServerExecutionService executionService;
    private AuditService auditService;
    private DeploymentJobRepository deploymentJobRepository;
    private CertificateInstallationRepository installationRepository;

    private NginxDeploymentAdapter adapter;

    @BeforeEach
    void setUp() {
        midServerClient = mock(MidServerClient.class);
        executionService = mock(MidServerExecutionService.class);
        auditService = mock(AuditService.class);
        deploymentJobRepository = mock(DeploymentJobRepository.class);
        installationRepository = mock(CertificateInstallationRepository.class);

        adapter = new NginxDeploymentAdapter(
                midServerClient,
                executionService,
                auditService,
                deploymentJobRepository,
                installationRepository
        );
    }

    private DeploymentJob createValidNginxJob(ServerOperatingSystem os) {
        MidServer midServer = new MidServer("MID-NGX-01", "https://mid-ngx01.internal:8443", MidServerStatus.UP);
        TargetServer targetServer = new TargetServer("nginx01.grassroots.internal", "10.0.7.15",
                os, ServerTechnology.NGINX, EnvironmentType.PRODUCTION);
        targetServer.setMidServer(midServer);

        CertificateRecord oldCert = new CertificateRecord();
        oldCert.setId(UUID.randomUUID());
        oldCert.setCommonName("gateway.grassroots.internal");
        oldCert.setThumbprint("OLDTHUMBPRINTNGINX1111111111111111111111");

        CertificateRecord newCert = new CertificateRecord();
        newCert.setId(UUID.randomUUID());
        newCert.setCommonName("gateway.grassroots.internal");
        newCert.setSerialNumber("SN-NGINX-2026");
        newCert.setThumbprint("NEWTHUMBPRINTNGINX2222222222222222222222");
        newCert.setValidTo(Instant.now().plus(365, ChronoUnit.DAYS));

        CertificateInstallation installation = new CertificateInstallation(
                oldCert, targetServer, ServerTechnology.NGINX, "gateway.grassroots.internal", 443
        );
        installation.setId(UUID.randomUUID());
        installation.setStatus(InstallationStatus.INSTALLED);

        DeploymentJob job = new DeploymentJob();
        job.setId(UUID.randomUUID());
        job.setJobReference("JOB-NGINX-001");
        job.setIdempotencyKey("IDEMP-NGINX-001");
        job.setTargetServer(targetServer);
        job.setOldCertificate(oldCert);
        job.setNewCertificate(newCert);
        job.setInstallation(installation);
        job.setDeploymentType(DeploymentType.RENEWAL_REPLACEMENT);
        job.setTargetPort(443);

        return job;
    }

    @Test
    @DisplayName("Metadata: supported technology and target type")
    void metadata_supportedTypes() {
        assertThat(adapter.getSupportedTechnology()).isEqualTo(ServerTechnology.NGINX);
        assertThat(adapter.getSupportedTargetType()).isEqualTo(TargetType.LINUX_NGINX);
    }

    @Test
    @DisplayName("Validation passes for valid Linux Ubuntu and RHEL Nginx servers")
    void validate_success() {
        DeploymentJob ubuntuJob = createValidNginxJob(ServerOperatingSystem.LINUX_UBUNTU);
        assertThat(adapter.supports(ubuntuJob)).isTrue();
        adapter.validate(ubuntuJob);

        DeploymentJob rhelJob = createValidNginxJob(ServerOperatingSystem.LINUX_RHEL);
        assertThat(adapter.supports(rhelJob)).isTrue();
        adapter.validate(rhelJob);
    }

    @Test
    @DisplayName("Validation fails when target OS is not Linux")
    void validate_fails_whenNotLinux() {
        DeploymentJob job = createValidNginxJob(ServerOperatingSystem.WINDOWS_SERVER);

        assertThatThrownBy(() -> adapter.validate(job))
                .isInstanceOf(LinuxValidationException.class)
                .hasMessageContaining("operating system must be a supported Linux distribution");
    }

    @Test
    @DisplayName("Validation fails when target technology is not Nginx")
    void validate_fails_whenNotNginx() {
        DeploymentJob job = createValidNginxJob(ServerOperatingSystem.LINUX_UBUNTU);
        job.getTargetServer().setTechnology(ServerTechnology.APACHE);

        assertThatThrownBy(() -> adapter.validate(job))
                .isInstanceOf(LinuxValidationException.class)
                .hasMessageContaining("technology must be NGINX");
    }

    @Test
    @DisplayName("Validation fails when certificate thumbprint is blank")
    void validate_fails_whenBlankThumbprint() {
        DeploymentJob job = createValidNginxJob(ServerOperatingSystem.LINUX_UBUNTU);
        job.getNewCertificate().setThumbprint("");

        assertThatThrownBy(() -> adapter.validate(job))
                .isInstanceOf(LinuxValidationException.class)
                .hasMessageContaining("must have a valid SHA-1/SHA-256 thumbprint");
    }

    @Test
    @DisplayName("Validation fails when assigned MID Server is DOWN")
    void validate_fails_whenMidServerDown() {
        DeploymentJob job = createValidNginxJob(ServerOperatingSystem.LINUX_UBUNTU);
        job.getTargetServer().getMidServer().setStatus(MidServerStatus.DOWN);

        assertThatThrownBy(() -> adapter.validate(job))
                .isInstanceOf(LinuxValidationException.class)
                .hasMessageContaining("is not UP");
    }

    @Test
    @DisplayName("1. Successful deployment: all 8 steps pass and verified thumbprint returned")
    void deploy_successful() {
        DeploymentJob job = createValidNginxJob(ServerOperatingSystem.LINUX_UBUNTU);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto dispatchResponse = new MidServerJobResponseDto(
                "MID-TASK-NGINX-001", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(dispatchResponse);

        MidServerStatusQueryResponseDto statusResponse = new MidServerStatusQueryResponseDto(
                "MID-TASK-NGINX-001", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.SUCCESS, 0,
                "Certificate bundle transferred. Key stored mode 0640. nginx -t syntax ok. nginx reloaded. TLS probe ok.",
                null, null, Instant.now().minusSeconds(10), Instant.now().minusSeconds(5), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-NGINX-001"))).thenReturn(statusResponse);

        DeploymentAdapterResult result = adapter.deploy(job);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getStatus()).isEqualTo(DeploymentAdapterResult.Status.SUCCESS);
        assertThat(result.getDeployedThumbprint()).isEqualTo("NEWTHUMBPRINTNGINX2222222222222222222222");
        assertThat(result.getVerifiedThumbprint()).isEqualTo("NEWTHUMBPRINTNGINX2222222222222222222222");
        assertThat(result.isRollbackExecuted()).isFalse();
        assertThat(result.getStepResults()).hasSize(8);

        // Verify request payload
        ArgumentCaptor<MidServerJobRequestDto> captor = ArgumentCaptor.forClass(MidServerJobRequestDto.class);
        verify(midServerClient).submitJob(eq(endpoint), captor.capture());
        MidServerJobRequestDto sentReq = captor.getValue();

        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("linux.technology")).isEqualTo("NGINX");
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("linux.configValidationCommand")).isEqualTo("/usr/sbin/nginx -t");
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("linux.serviceReloadCommand")).isEqualTo("/bin/systemctl reload nginx");
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("linux.certificatePath")).contains(".bundle.crt");
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("linux.certFileMode")).isEqualTo("0644");
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("linux.keyFileMode")).isEqualTo("0640");

        // Verify zero private keys in logs/parameters (only vault reference)
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("linux.vaultSecretReference")).contains("cyberark://");

        // Verify audit event and installation repository update
        verify(auditService).recordAudit(any(AuditEvent.class));
        verify(installationRepository).saveAndFlush(any(CertificateInstallation.class));
    }

    @Test
    @DisplayName("2. Certificate transfer failure: throws LinuxCertificateTransferException without rollback")
    void deploy_certificateTransferFailure() {
        DeploymentJob job = createValidNginxJob(ServerOperatingSystem.LINUX_UBUNTU);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto dispatchResponse = new MidServerJobResponseDto(
                "MID-TASK-CERT-FAIL-NGX", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(dispatchResponse);

        MidServerStatusQueryResponseDto statusResponse = new MidServerStatusQueryResponseDto(
                "MID-TASK-CERT-FAIL-NGX", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                AbstractLinuxDeploymentAdapter.EXIT_CERT_TRANSFER_FAILED, null, "SFTP error: permission denied writing /etc/ssl/certs",
                "certificate transfer failed", Instant.now(), Instant.now(), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-CERT-FAIL-NGX"))).thenReturn(statusResponse);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(LinuxCertificateTransferException.class)
                .hasMessageContaining("certificate transfer failed");

        verify(midServerClient, times(1)).submitJob(any(), any());
    }

    @Test
    @DisplayName("3. Private key transfer failure: throws LinuxPrivateKeyTransferException")
    void deploy_privateKeyTransferFailure() {
        DeploymentJob job = createValidNginxJob(ServerOperatingSystem.LINUX_UBUNTU);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto dispatchResponse = new MidServerJobResponseDto(
                "MID-TASK-KEY-FAIL-NGX", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(dispatchResponse);

        MidServerStatusQueryResponseDto statusResponse = new MidServerStatusQueryResponseDto(
                "MID-TASK-KEY-FAIL-NGX", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                AbstractLinuxDeploymentAdapter.EXIT_KEY_TRANSFER_FAILED, null, "Vault error 404",
                "private key transfer failed: secret not found in vault", Instant.now(), Instant.now(), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-KEY-FAIL-NGX"))).thenReturn(statusResponse);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(LinuxPrivateKeyTransferException.class)
                .hasMessageContaining("private key transfer failed");

        verify(midServerClient, times(1)).submitJob(any(), any());
    }

    @Test
    @DisplayName("4. Permission application failure: throws LinuxPermissionException")
    void deploy_permissionFailure() {
        DeploymentJob job = createValidNginxJob(ServerOperatingSystem.LINUX_RHEL);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto dispatchResponse = new MidServerJobResponseDto(
                "MID-TASK-PERM-FAIL-NGX", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(dispatchResponse);

        MidServerStatusQueryResponseDto statusResponse = new MidServerStatusQueryResponseDto(
                "MID-TASK-PERM-FAIL-NGX", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                AbstractLinuxDeploymentAdapter.EXIT_PERMISSIONS_FAILED, null, "chown root:nginx failed",
                "permission failed on private key", Instant.now(), Instant.now(), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-PERM-FAIL-NGX"))).thenReturn(statusResponse);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(LinuxPermissionException.class)
                .hasMessageContaining("Permission enforcement failed");

        verify(midServerClient, times(1)).submitJob(any(), any());
    }

    @Test
    @DisplayName("5. Configuration syntax validation failure: nginx -t fails, stopping deployment without service disruption")
    void deploy_configurationValidationFailure() {
        DeploymentJob job = createValidNginxJob(ServerOperatingSystem.LINUX_UBUNTU);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto dispatchResponse = new MidServerJobResponseDto(
                "MID-TASK-NGX-CONF-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(dispatchResponse);

        MidServerStatusQueryResponseDto statusResponse = new MidServerStatusQueryResponseDto(
                "MID-TASK-NGX-CONF-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                AbstractLinuxDeploymentAdapter.EXIT_CONFIG_VALIDATION_FAILED, null, "nginx: [emerg] invalid directive 'ssl_certificat'",
                "configuration validation failed", Instant.now(), Instant.now(), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-NGX-CONF-FAIL"))).thenReturn(statusResponse);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(LinuxConfigurationValidationException.class)
                .hasMessageContaining("Configuration syntax validation failed");

        // Service reload never called
        verify(midServerClient, times(1)).submitJob(any(), any());
    }

    @Test
    @DisplayName("6. Service reload failure: triggers automated rollback restoring Nginx configuration backup")
    void deploy_reloadFailure_triggersRollback() {
        DeploymentJob job = createValidNginxJob(ServerOperatingSystem.LINUX_UBUNTU);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto deployDispatch = new MidServerJobResponseDto(
                "MID-TASK-NGX-RELOAD-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        MidServerJobResponseDto rollbackDispatch = new MidServerJobResponseDto(
                "MID-TASK-ROLLBACK-NGX-01", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );

        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class)))
                .thenReturn(deployDispatch)
                .thenReturn(rollbackDispatch);

        MidServerStatusQueryResponseDto deployStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-NGX-RELOAD-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                AbstractLinuxDeploymentAdapter.EXIT_SERVICE_RELOAD_FAILED, null, "systemctl reload nginx failed: status=1",
                "reload failed", Instant.now(), Instant.now(), Instant.now()
        );
        MidServerStatusQueryResponseDto rollbackStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-ROLLBACK-NGX-01", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.SUCCESS,
                0, "Restored previous nginx config from .cdm-bak and reloaded nginx successfully", null, null,
                Instant.now(), Instant.now(), Instant.now()
        );

        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-NGX-RELOAD-FAIL"))).thenReturn(deployStatus);
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-ROLLBACK-NGX-01"))).thenReturn(rollbackStatus);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(LinuxServiceReloadException.class)
                .hasMessageContaining("Graceful service reload failed");

        verify(midServerClient, times(2)).submitJob(eq(endpoint), any(MidServerJobRequestDto.class));
    }

    @Test
    @DisplayName("7. Verification failure: live endpoint mismatch triggers automated rollback")
    void deploy_verificationFailure_triggersRollback() {
        DeploymentJob job = createValidNginxJob(ServerOperatingSystem.LINUX_UBUNTU);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto deployDispatch = new MidServerJobResponseDto(
                "MID-TASK-NGX-VERIFY-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        MidServerJobResponseDto rollbackDispatch = new MidServerJobResponseDto(
                "MID-TASK-ROLLBACK-NGX-02", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );

        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class)))
                .thenReturn(deployDispatch)
                .thenReturn(rollbackDispatch);

        MidServerStatusQueryResponseDto deployStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-NGX-VERIFY-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                AbstractLinuxDeploymentAdapter.EXIT_VERIFICATION_FAILED, null, "Negotiated TLS cert thumbprint mismatch",
                "verification failed", Instant.now(), Instant.now(), Instant.now()
        );
        MidServerStatusQueryResponseDto rollbackStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-ROLLBACK-NGX-02", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.SUCCESS,
                0, "Restored Nginx configuration backup successfully", null, null, Instant.now(), Instant.now(), Instant.now()
        );

        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-NGX-VERIFY-FAIL"))).thenReturn(deployStatus);
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-ROLLBACK-NGX-02"))).thenReturn(rollbackStatus);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(LinuxVerificationException.class)
                .hasMessageContaining("Live TLS endpoint verification failed");

        verify(midServerClient, times(2)).submitJob(eq(endpoint), any(MidServerJobRequestDto.class));
    }

    @Test
    @DisplayName("8. Repeated deployment: idempotent execution skips remote MID Server call")
    void deploy_repeatedDeployment_skipsExecution() {
        DeploymentJob job = createValidNginxJob(ServerOperatingSystem.LINUX_UBUNTU);
        job.getInstallation().setCertificate(job.getNewCertificate());
        job.getInstallation().setStatus(InstallationStatus.INSTALLED);

        DeploymentAdapterResult result = adapter.deploy(job);

        assertThat(result.isIdempotent()).isTrue();
        assertThat(result.getStatus()).isEqualTo(DeploymentAdapterResult.Status.SKIPPED_IDEMPOTENT);
        assertThat(result.getDeployedThumbprint()).isEqualTo("NEWTHUMBPRINTNGINX2222222222222222222222");

        verify(midServerClient, never()).submitJob(any(), any());
    }

    @Test
    @DisplayName("9. Rollback: manual/explicit rollback successfully restores configuration")
    void rollback_successful() {
        DeploymentJob job = createValidNginxJob(ServerOperatingSystem.LINUX_UBUNTU);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto rollbackDispatch = new MidServerJobResponseDto(
                "MID-TASK-EXPLICIT-ROLLBACK-NGX", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(),
                MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(rollbackDispatch);

        MidServerStatusQueryResponseDto rollbackStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-EXPLICIT-ROLLBACK-NGX", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(),
                MidServerExecutionState.SUCCESS, 0, "Configuration restored from .cdm-bak and nginx reloaded",
                null, null, Instant.now(), Instant.now(), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-EXPLICIT-ROLLBACK-NGX"))).thenReturn(rollbackStatus);

        DeploymentAdapterResult rollbackResult = adapter.rollback(job, "Operator requested revert");

        assertThat(rollbackResult.getStatus()).isEqualTo(DeploymentAdapterResult.Status.ROLLED_BACK);
        assertThat(rollbackResult.isRollbackExecuted()).isTrue();
        assertThat(rollbackResult.getDeployedThumbprint()).isEqualTo("OLDTHUMBPRINTNGINX1111111111111111111111");

        ArgumentCaptor<MidServerJobRequestDto> captor = ArgumentCaptor.forClass(MidServerJobRequestDto.class);
        verify(midServerClient).submitJob(eq(endpoint), captor.capture());

        MidServerJobRequestDto sentReq = captor.getValue();
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("linux.operation")).isEqualTo("ROLLBACK");
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("linux.configValidationCommand")).isEqualTo("/usr/sbin/nginx -t");
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("linux.serviceReloadCommand")).isEqualTo("/bin/systemctl reload nginx");
    }
}
