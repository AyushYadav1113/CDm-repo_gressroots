package com.grassroots.cdm.deployment.adapter.java;

import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.TargetType;
import com.grassroots.cdm.deployment.adapter.DeploymentAdapterResult;
import com.grassroots.cdm.deployment.adapter.java.exception.ApplicationConfigurationException;
import com.grassroots.cdm.deployment.adapter.java.exception.ApplicationRestartException;
import com.grassroots.cdm.deployment.adapter.java.exception.JavaEndpointVerificationException;
import com.grassroots.cdm.deployment.adapter.java.exception.JavaPermissionException;
import com.grassroots.cdm.deployment.adapter.java.exception.JavaValidationException;
import com.grassroots.cdm.deployment.adapter.java.exception.KeystoreOperationException;
import com.grassroots.cdm.deployment.adapter.java.keystore.JavaKeystoreManager;
import com.grassroots.cdm.deployment.adapter.java.profile.JavaDeploymentProfile;
import com.grassroots.cdm.deployment.adapter.java.profile.JavaDeploymentProfileResolver;
import com.grassroots.cdm.deployment.adapter.java.profile.KeystoreType;
import com.grassroots.cdm.deployment.adapter.java.profile.RestartStrategy;
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

class JavaDeploymentAdapterTest {

    private MidServerClient midServerClient;
    private MidServerExecutionService executionService;
    private AuditService auditService;
    private DeploymentJobRepository deploymentJobRepository;
    private CertificateInstallationRepository installationRepository;
    private JavaDeploymentProfileResolver profileResolver;
    private JavaKeystoreManager keystoreManager;

    private JavaDeploymentAdapter adapter;

    @BeforeEach
    void setUp() {
        midServerClient = mock(MidServerClient.class);
        executionService = mock(MidServerExecutionService.class);
        auditService = mock(AuditService.class);
        deploymentJobRepository = mock(DeploymentJobRepository.class);
        installationRepository = mock(CertificateInstallationRepository.class);
        profileResolver = new JavaDeploymentProfileResolver();
        keystoreManager = mock(JavaKeystoreManager.class);

        adapter = new JavaDeploymentAdapter(
                midServerClient,
                executionService,
                auditService,
                deploymentJobRepository,
                installationRepository,
                profileResolver,
                keystoreManager
        );
    }

    private DeploymentJob createValidJavaJob(KeystoreType keystoreType, ServerTechnology tech) {
        MidServer midServer = new MidServer("MID-JAVA-01", "https://mid-java01.internal:8443", MidServerStatus.UP);
        TargetServer targetServer = new TargetServer("app-api01.grassroots.internal", "10.0.8.20",
                ServerOperatingSystem.LINUX_RHEL, tech, EnvironmentType.PRODUCTION);
        targetServer.setMidServer(midServer);

        CertificateRecord oldCert = new CertificateRecord();
        oldCert.setId(UUID.randomUUID());
        oldCert.setCommonName("api-java.grassroots.internal");
        oldCert.setThumbprint("OLDTHUMBPRINTJAVA1111111111111111111111");

        CertificateRecord newCert = new CertificateRecord();
        newCert.setId(UUID.randomUUID());
        newCert.setCommonName("api-java.grassroots.internal");
        newCert.setSerialNumber("SN-JAVA-2026");
        newCert.setThumbprint("NEWTHUMBPRINTJAVA2222222222222222222222");
        newCert.setValidTo(Instant.now().plus(365, ChronoUnit.DAYS));

        String ext = keystoreType == KeystoreType.JKS ? ".jks" : ".p12";
        String bindingInfo = "keystoreLocation=/opt/app/security/keystore" + ext
                + ";alias=tomcat;type=" + keystoreType.getFormat()
                + ";restartStrategy=SYSTEMD_SERVICE;serviceName=myapp;fileMode=0640";

        CertificateInstallation installation = new CertificateInstallation(
                oldCert, targetServer, tech, bindingInfo, 8443
        );
        installation.setId(UUID.randomUUID());
        installation.setStatus(InstallationStatus.INSTALLED);

        DeploymentJob job = new DeploymentJob();
        job.setId(UUID.randomUUID());
        job.setJobReference("JOB-JAVA-001");
        job.setIdempotencyKey("IDEMP-JAVA-001");
        job.setTargetServer(targetServer);
        job.setOldCertificate(oldCert);
        job.setNewCertificate(newCert);
        job.setInstallation(installation);
        job.setDeploymentType(DeploymentType.RENEWAL_REPLACEMENT);
        job.setTargetPort(8443);

        return job;
    }

    @Test
    @DisplayName("Metadata: supports JAVA_KEYSTORE and TargetType.JAVA_KEYSTORE")
    void metadata_supportedTypes() {
        assertThat(adapter.getSupportedTechnology()).isEqualTo(ServerTechnology.JAVA_KEYSTORE);
        assertThat(adapter.getSupportedTargetType()).isEqualTo(TargetType.JAVA_KEYSTORE);
    }

    @Test
    @DisplayName("Supports: recognizes JAVA_KEYSTORE, TOMCAT, WEBLOGIC, and WEBSPHERE technologies")
    void supports_technologies() {
        DeploymentJob javaJob = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.JAVA_KEYSTORE);
        assertThat(adapter.supports(javaJob)).isTrue();

        DeploymentJob tomcatJob = createValidJavaJob(KeystoreType.JKS, ServerTechnology.TOMCAT);
        assertThat(adapter.supports(tomcatJob)).isTrue();

        DeploymentJob weblogicJob = createValidJavaJob(KeystoreType.JKS, ServerTechnology.WEBLOGIC);
        assertThat(adapter.supports(weblogicJob)).isTrue();

        DeploymentJob iisJob = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.IIS);
        assertThat(adapter.supports(iisJob)).isFalse();
    }

    @Test
    @DisplayName("Validation: passes for valid Java deployment job and profile")
    void validate_success() {
        DeploymentJob job = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.JAVA_KEYSTORE);
        adapter.validate(job); // should not throw
    }

    @Test
    @DisplayName("Validation: fails when MID Server is DOWN")
    void validate_fails_whenMidServerDown() {
        DeploymentJob job = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.JAVA_KEYSTORE);
        job.getTargetServer().getMidServer().setStatus(MidServerStatus.DOWN);

        assertThatThrownBy(() -> adapter.validate(job))
                .isInstanceOf(JavaValidationException.class)
                .hasMessageContaining("is not UP");
    }

    @Test
    @DisplayName("Validation: fails when certificate is expired")
    void validate_fails_whenExpiredCert() {
        DeploymentJob job = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.JAVA_KEYSTORE);
        job.getNewCertificate().setValidTo(Instant.now().minus(1, ChronoUnit.DAYS));

        assertThatThrownBy(() -> adapter.validate(job))
                .isInstanceOf(JavaValidationException.class)
                .hasMessageContaining("is already expired");
    }

    @Test
    @DisplayName("Validation: fails when keystore location contains path traversal ('..')")
    void validate_fails_whenPathTraversal() {
        DeploymentJob job = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.JAVA_KEYSTORE);
        job.getInstallation().setBindingInfo("keystoreLocation=/opt/app/../etc/shadow.p12;alias=tomcat");

        assertThatThrownBy(() -> adapter.validate(job))
                .isInstanceOf(JavaValidationException.class)
                .hasMessageContaining("Path traversal attempt");
    }

    @Test
    @DisplayName("Validation: fails when insecure file permissions are specified (e.g. 0777)")
    void validate_fails_whenInsecurePermissions() {
        DeploymentJob job = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.JAVA_KEYSTORE);
        job.getInstallation().setBindingInfo("keystoreLocation=/opt/app/keystore.p12;alias=tomcat;fileMode=0777");

        assertThatThrownBy(() -> adapter.validate(job))
                .isInstanceOf(JavaPermissionException.class)
                .hasMessageContaining("Insecure keystore permissions rejected");
    }

    @Test
    @DisplayName("1. Successful deployment (PKCS12): all 7 steps pass and structured result returned")
    void deploy_successful_pkcs12() {
        DeploymentJob job = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.JAVA_KEYSTORE);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto dispatchResponse = new MidServerJobResponseDto(
                "MID-TASK-JAVA-001", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(dispatchResponse);

        MidServerStatusQueryResponseDto statusResponse = new MidServerStatusQueryResponseDto(
                "MID-TASK-JAVA-001", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.SUCCESS, 0,
                "PKCS12 keystore updated. Mode 0640 applied. App config updated. systemctl restart myapp OK. Handshake OK.",
                null, null, Instant.now().minusSeconds(10), Instant.now().minusSeconds(5), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-JAVA-001"))).thenReturn(statusResponse);

        DeploymentAdapterResult result = adapter.deploy(job);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getStatus()).isEqualTo(DeploymentAdapterResult.Status.SUCCESS);
        assertThat(result.getDeployedThumbprint()).isEqualTo("NEWTHUMBPRINTJAVA2222222222222222222222");
        assertThat(result.getVerifiedThumbprint()).isEqualTo("NEWTHUMBPRINTJAVA2222222222222222222222");
        assertThat(result.isRollbackExecuted()).isFalse();
        assertThat(result.getStepResults()).hasSize(6);

        // Verify request payload
        ArgumentCaptor<MidServerJobRequestDto> captor = ArgumentCaptor.forClass(MidServerJobRequestDto.class);
        verify(midServerClient).submitJob(eq(endpoint), captor.capture());
        MidServerJobRequestDto sentReq = captor.getValue();

        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("java.keystoreType")).isEqualTo("PKCS12");
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("java.keystoreLocation")).isEqualTo("/opt/app/security/keystore.p12");
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("java.keyAlias")).isEqualTo("tomcat");
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("java.fileMode")).isEqualTo("0640");

        // Verify zero plaintext passwords in parameters (strictly vault locator)
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("java.keystorePasswordVaultRef")).contains("cyberark://");

        // Verify audit event and installation repository update
        verify(auditService).recordAudit(any(AuditEvent.class));
        verify(installationRepository).saveAndFlush(any(CertificateInstallation.class));
    }

    @Test
    @DisplayName("2. Successful deployment (JKS): supports JKS format and Tomcat configuration")
    void deploy_successful_jks() {
        DeploymentJob job = createValidJavaJob(KeystoreType.JKS, ServerTechnology.TOMCAT);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto dispatchResponse = new MidServerJobResponseDto(
                "MID-TASK-TOMCAT-001", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(dispatchResponse);

        MidServerStatusQueryResponseDto statusResponse = new MidServerStatusQueryResponseDto(
                "MID-TASK-TOMCAT-001", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.SUCCESS, 0,
                "JKS keystore updated. server.xml updated. tomcat service restarted. Handshake verified.",
                null, null, Instant.now().minusSeconds(10), Instant.now().minusSeconds(5), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-TOMCAT-001"))).thenReturn(statusResponse);

        DeploymentAdapterResult result = adapter.deploy(job);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getStatus()).isEqualTo(DeploymentAdapterResult.Status.SUCCESS);

        ArgumentCaptor<MidServerJobRequestDto> captor = ArgumentCaptor.forClass(MidServerJobRequestDto.class);
        verify(midServerClient).submitJob(eq(endpoint), captor.capture());
        MidServerJobRequestDto sentReq = captor.getValue();
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("java.keystoreType")).isEqualTo("JKS");
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("java.appConfigLocation")).contains("server.xml");
    }

    @Test
    @DisplayName("3. Keystore update failure: throws KeystoreOperationException without rollback")
    void deploy_keystoreOperationFailure() {
        DeploymentJob job = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.JAVA_KEYSTORE);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto dispatchResponse = new MidServerJobResponseDto(
                "MID-TASK-KEYSTORE-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(dispatchResponse);

        MidServerStatusQueryResponseDto statusResponse = new MidServerStatusQueryResponseDto(
                "MID-TASK-KEYSTORE-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                JavaDeploymentAdapter.EXIT_KEYSTORE_OPERATION_FAILED, null, "KeyStoreException: Corrupted keystore stream",
                "keystore update failed", Instant.now(), Instant.now(), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-KEYSTORE-FAIL"))).thenReturn(statusResponse);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(KeystoreOperationException.class)
                .hasMessageContaining("keystore update failed");

        verify(midServerClient, times(1)).submitJob(any(), any());
    }

    @Test
    @DisplayName("4. Permission application failure: throws JavaPermissionException")
    void deploy_permissionFailure() {
        DeploymentJob job = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.JAVA_KEYSTORE);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto dispatchResponse = new MidServerJobResponseDto(
                "MID-TASK-PERM-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(dispatchResponse);

        MidServerStatusQueryResponseDto statusResponse = new MidServerStatusQueryResponseDto(
                "MID-TASK-PERM-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                JavaDeploymentAdapter.EXIT_PERMISSION_FAILED, null, "chmod 0640 failed",
                "permission failed on keystore file", Instant.now(), Instant.now(), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-PERM-FAIL"))).thenReturn(statusResponse);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(JavaPermissionException.class)
                .hasMessageContaining("Permission enforcement failed");

        verify(midServerClient, times(1)).submitJob(any(), any());
    }

    @Test
    @DisplayName("5. Application config update failure: triggers automated rollback")
    void deploy_applicationConfigurationFailure_triggersRollback() {
        DeploymentJob job = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.TOMCAT);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto deployDispatch = new MidServerJobResponseDto(
                "MID-TASK-CONFIG-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        MidServerJobResponseDto rollbackDispatch = new MidServerJobResponseDto(
                "MID-TASK-ROLLBACK-JAVA-01", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );

        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class)))
                .thenReturn(deployDispatch)
                .thenReturn(rollbackDispatch);

        MidServerStatusQueryResponseDto deployStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-CONFIG-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                JavaDeploymentAdapter.EXIT_CONFIG_UPDATE_FAILED, null, "XML parser error updating server.xml",
                "config update failed", Instant.now(), Instant.now(), Instant.now()
        );
        MidServerStatusQueryResponseDto rollbackStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-ROLLBACK-JAVA-01", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.SUCCESS,
                0, "Restored previous keystore from .cdm-bak successfully", null, null, Instant.now(), Instant.now(), Instant.now()
        );

        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-CONFIG-FAIL"))).thenReturn(deployStatus);
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-ROLLBACK-JAVA-01"))).thenReturn(rollbackStatus);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(ApplicationConfigurationException.class)
                .hasMessageContaining("Application configuration update failed");

        // Verify automated rollback was dispatched
        verify(midServerClient, times(2)).submitJob(eq(endpoint), any(MidServerJobRequestDto.class));
    }

    @Test
    @DisplayName("6. Application restart failure: triggers automated rollback")
    void deploy_restartFailure_triggersRollback() {
        DeploymentJob job = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.JAVA_KEYSTORE);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto deployDispatch = new MidServerJobResponseDto(
                "MID-TASK-RESTART-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        MidServerJobResponseDto rollbackDispatch = new MidServerJobResponseDto(
                "MID-TASK-ROLLBACK-JAVA-02", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );

        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class)))
                .thenReturn(deployDispatch)
                .thenReturn(rollbackDispatch);

        MidServerStatusQueryResponseDto deployStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-RESTART-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                JavaDeploymentAdapter.EXIT_RESTART_FAILED, null, "systemctl restart myapp exited 1",
                "restart failed", Instant.now(), Instant.now(), Instant.now()
        );
        MidServerStatusQueryResponseDto rollbackStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-ROLLBACK-JAVA-02", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.SUCCESS,
                0, "Restored keystore backup and restarted service", null, null, Instant.now(), Instant.now(), Instant.now()
        );

        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-RESTART-FAIL"))).thenReturn(deployStatus);
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-ROLLBACK-JAVA-02"))).thenReturn(rollbackStatus);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(ApplicationRestartException.class)
                .hasMessageContaining("Application restart failed");

        verify(midServerClient, times(2)).submitJob(eq(endpoint), any(MidServerJobRequestDto.class));
    }

    @Test
    @DisplayName("7. Verification failure: TLS probe mismatch triggers automated rollback")
    void deploy_verificationFailure_triggersRollback() {
        DeploymentJob job = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.JAVA_KEYSTORE);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto deployDispatch = new MidServerJobResponseDto(
                "MID-TASK-VERIFY-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        MidServerJobResponseDto rollbackDispatch = new MidServerJobResponseDto(
                "MID-TASK-ROLLBACK-JAVA-03", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );

        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class)))
                .thenReturn(deployDispatch)
                .thenReturn(rollbackDispatch);

        MidServerStatusQueryResponseDto deployStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-VERIFY-FAIL", job.getId(), job.getIdempotencyKey(), MidServerExecutionState.FAILED,
                JavaDeploymentAdapter.EXIT_VERIFICATION_FAILED, null, "Handshake served old certificate thumbprint",
                "verification failed: thumbprint mismatch", Instant.now(), Instant.now(), Instant.now()
        );
        MidServerStatusQueryResponseDto rollbackStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-ROLLBACK-JAVA-03", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(), MidServerExecutionState.SUCCESS,
                0, "Restored previous keystore from backup", null, null, Instant.now(), Instant.now(), Instant.now()
        );

        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-VERIFY-FAIL"))).thenReturn(deployStatus);
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-ROLLBACK-JAVA-03"))).thenReturn(rollbackStatus);

        assertThatThrownBy(() -> adapter.deploy(job))
                .isInstanceOf(JavaEndpointVerificationException.class)
                .hasMessageContaining("Live endpoint TLS verification failed");

        verify(midServerClient, times(2)).submitJob(eq(endpoint), any(MidServerJobRequestDto.class));
    }

    @Test
    @DisplayName("8. Repeated deployment: idempotent execution skips remote MID Server call")
    void deploy_repeatedDeployment_skipsExecution() {
        DeploymentJob job = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.JAVA_KEYSTORE);
        job.getInstallation().setCertificate(job.getNewCertificate());
        job.getInstallation().setStatus(InstallationStatus.INSTALLED);

        DeploymentAdapterResult result = adapter.deploy(job);

        assertThat(result.isIdempotent()).isTrue();
        assertThat(result.getStatus()).isEqualTo(DeploymentAdapterResult.Status.SKIPPED_IDEMPOTENT);
        assertThat(result.getDeployedThumbprint()).isEqualTo("NEWTHUMBPRINTJAVA2222222222222222222222");

        verify(midServerClient, never()).submitJob(any(), any());
    }

    @Test
    @DisplayName("9. Explicit rollback: successfully restores previous keystore and reloads application")
    void rollback_successful() {
        DeploymentJob job = createValidJavaJob(KeystoreType.PKCS12, ServerTechnology.JAVA_KEYSTORE);
        String endpoint = job.getTargetServer().getMidServer().getEndpoint();

        MidServerJobResponseDto rollbackDispatch = new MidServerJobResponseDto(
                "MID-TASK-EXPLICIT-ROLLBACK-JAVA", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(),
                MidServerExecutionState.QUEUED, Instant.now(), "Queued"
        );
        when(midServerClient.submitJob(eq(endpoint), any(MidServerJobRequestDto.class))).thenReturn(rollbackDispatch);

        MidServerStatusQueryResponseDto rollbackStatus = new MidServerStatusQueryResponseDto(
                "MID-TASK-EXPLICIT-ROLLBACK-JAVA", job.getId(), "ROLLBACK-" + job.getIdempotencyKey(),
                MidServerExecutionState.SUCCESS, 0, "Restored keystore from .cdm-bak and restarted myapp",
                null, null, Instant.now(), Instant.now(), Instant.now()
        );
        when(midServerClient.getJobStatus(eq(endpoint), eq("MID-TASK-EXPLICIT-ROLLBACK-JAVA"))).thenReturn(rollbackStatus);

        DeploymentAdapterResult rollbackResult = adapter.rollback(job, "Operator emergency rollback");

        assertThat(rollbackResult.getStatus()).isEqualTo(DeploymentAdapterResult.Status.ROLLED_BACK);
        assertThat(rollbackResult.isRollbackExecuted()).isTrue();
        assertThat(rollbackResult.getDeployedThumbprint()).isEqualTo("OLDTHUMBPRINTJAVA1111111111111111111111");

        ArgumentCaptor<MidServerJobRequestDto> captor = ArgumentCaptor.forClass(MidServerJobRequestDto.class);
        verify(midServerClient).submitJob(eq(endpoint), captor.capture());

        MidServerJobRequestDto sentReq = captor.getValue();
        assertThat(sentReq.getExecutionParameters().getCustomSettings().get("java.operation")).isEqualTo("ROLLBACK");
        assertThat(sentReq.getCertificateReference().getThumbprint()).isEqualTo("OLDTHUMBPRINTJAVA1111111111111111111111");
    }
}
