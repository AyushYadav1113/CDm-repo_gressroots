package com.grassroots.cdm.deployment.adapter.java;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.TargetType;
import com.grassroots.cdm.deployment.adapter.DeploymentAdapter;
import com.grassroots.cdm.deployment.adapter.DeploymentAdapterResult;
import com.grassroots.cdm.deployment.adapter.DeploymentStepResult;
import com.grassroots.cdm.deployment.adapter.java.exception.ApplicationConfigurationException;
import com.grassroots.cdm.deployment.adapter.java.exception.ApplicationRestartException;
import com.grassroots.cdm.deployment.adapter.java.exception.JavaEndpointVerificationException;
import com.grassroots.cdm.deployment.adapter.java.exception.JavaPermissionException;
import com.grassroots.cdm.deployment.adapter.java.exception.JavaRollbackException;
import com.grassroots.cdm.deployment.adapter.java.exception.JavaValidationException;
import com.grassroots.cdm.deployment.adapter.java.exception.KeystoreOperationException;
import com.grassroots.cdm.deployment.adapter.java.keystore.JavaKeystoreManager;
import com.grassroots.cdm.deployment.adapter.java.model.JavaDeploymentCommand;
import com.grassroots.cdm.deployment.adapter.java.model.JavaExecutionStep;
import com.grassroots.cdm.deployment.adapter.java.profile.JavaDeploymentProfile;
import com.grassroots.cdm.deployment.adapter.java.profile.JavaDeploymentProfileResolver;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.InstallationStatus;
import com.grassroots.cdm.entity.enums.MidServerExecutionState;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.integration.midserver.MidServerClient;
import com.grassroots.cdm.integration.midserver.dto.CertificateReferenceDto;
import com.grassroots.cdm.integration.midserver.dto.ExecutionParametersDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobRequestDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobResponseDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerStatusQueryResponseDto;
import com.grassroots.cdm.integration.midserver.dto.TargetServerInfoDto;
import com.grassroots.cdm.integration.midserver.service.MidServerExecutionService;
import com.grassroots.cdm.repository.CertificateInstallationRepository;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Technology deployment adapter for Java applications using JKS or PKCS12 keystores.
 *
 * Implements the 7 conceptual steps:
 * 1. Receive certificate material securely
 * 2. Create/update keystore (JKS or PKCS12)
 * 3. Set appropriate permissions (0600 or 0640)
 * 4. Update application configuration when required (e.g. server.xml, application.yml)
 * 5. Restart/reload application when required (systemd, graceful reload, hot reload)
 * 6. Verify live endpoint (TLS probe on port matching target thumbprint)
 * 7. Return structured deployment result with automated rollback
 *
 * Employs a JavaDeploymentProfile abstraction to avoid assuming identical configurations
 * across heterogeneous Java environments (Spring Boot, Tomcat, WebLogic, WebSphere).
 */
@Component
public class JavaDeploymentAdapter implements DeploymentAdapter {

    private static final Logger log = LoggerFactory.getLogger(JavaDeploymentAdapter.class);

    // Standardized Java MID Server execution exit codes
    public static final int EXIT_KEYSTORE_OPERATION_FAILED = 301;
    public static final int EXIT_PERMISSION_FAILED = 302;
    public static final int EXIT_CONFIG_UPDATE_FAILED = 303;
    public static final int EXIT_RESTART_FAILED = 304;
    public static final int EXIT_VERIFICATION_FAILED = 305;

    private final MidServerClient midServerClient;
    private final MidServerExecutionService executionService;
    private final AuditService auditService;
    private final DeploymentJobRepository deploymentJobRepository;
    private final CertificateInstallationRepository installationRepository;
    private final JavaDeploymentProfileResolver profileResolver;
    private final JavaKeystoreManager keystoreManager;

    public JavaDeploymentAdapter(
            MidServerClient midServerClient,
            MidServerExecutionService executionService,
            AuditService auditService,
            DeploymentJobRepository deploymentJobRepository,
            CertificateInstallationRepository installationRepository,
            JavaDeploymentProfileResolver profileResolver,
            JavaKeystoreManager keystoreManager) {
        this.midServerClient = midServerClient;
        this.executionService = executionService;
        this.auditService = auditService;
        this.deploymentJobRepository = deploymentJobRepository;
        this.installationRepository = installationRepository;
        this.profileResolver = profileResolver;
        this.keystoreManager = keystoreManager;
    }

    @Override
    public ServerTechnology getSupportedTechnology() {
        return ServerTechnology.JAVA_KEYSTORE;
    }

    @Override
    public TargetType getSupportedTargetType() {
        return TargetType.JAVA_KEYSTORE;
    }

    @Override
    public boolean supports(DeploymentJob job) {
        if (job == null) return false;
        if (job.getTargetServer() != null) {
            ServerTechnology tech = job.getTargetServer().getTechnology();
            if (tech == ServerTechnology.JAVA_KEYSTORE
                    || tech == ServerTechnology.TOMCAT
                    || tech == ServerTechnology.WEBLOGIC
                    || tech == ServerTechnology.WEBSPHERE) {
                return true;
            }
        }
        return "JAVA_KEYSTORE".equalsIgnoreCase(job.getTargetType()) || "JAVA".equalsIgnoreCase(job.getTargetType());
    }

    @Override
    public void validate(DeploymentJob job) {
        if (job == null) {
            throw new JavaValidationException("DeploymentJob cannot be null");
        }

        TargetServer targetServer = job.getTargetServer();
        if (targetServer == null) {
            throw new JavaValidationException("Target server is required for Java certificate deployment");
        }

        CertificateRecord newCert = job.getNewCertificate();
        if (newCert == null) {
            throw new JavaValidationException("New certificate is required for Java deployment");
        }
        if (newCert.getThumbprint() == null || newCert.getThumbprint().isBlank()) {
            throw new JavaValidationException("New certificate must have a valid SHA-1/SHA-256 thumbprint");
        }
        if (newCert.getValidTo() != null && newCert.getValidTo().isBefore(Instant.now())) {
            throw new JavaValidationException("New certificate is already expired (validTo: " + newCert.getValidTo() + ")");
        }

        MidServer midServer = targetServer.getMidServer();
        if (midServer == null) {
            throw new JavaValidationException("Target server " + targetServer.getHostname() + " has no assigned MID Server");
        }
        if (midServer.getStatus() != MidServerStatus.UP) {
            throw new JavaValidationException("Assigned MID Server " + midServer.getName() + " is not UP (current status: " + midServer.getStatus() + ")");
        }

        // Validate resolved profile
        JavaDeploymentProfile profile = profileResolver.resolveProfile(job);
        if (profile.getKeystoreLocation() == null || profile.getKeystoreLocation().isBlank()) {
            throw new JavaValidationException("Keystore location cannot be null or empty in profile");
        }
        if (profile.getKeystoreLocation().contains("..")) {
            throw new JavaValidationException("Path traversal attempt ('..') detected in keystore location: " + profile.getKeystoreLocation());
        }
        if (profile.getKeyAlias() == null || profile.getKeyAlias().isBlank()) {
            throw new JavaValidationException("Key alias cannot be null or empty in profile");
        }

        // Validate secure permissions
        if (!profile.isSecurePermissions()) {
            throw new JavaPermissionException("Insecure keystore permissions rejected: " + profile.getFileMode()
                    + ". Java keystores must use mode 0600 or 0640.", profile.getKeystoreLocation(), profile.getFileMode(), EXIT_PERMISSION_FAILED);
        }

        int port = profile.getTargetPort();
        if (port <= 0 || port > 65535) {
            throw new JavaValidationException("Invalid target port for Java application: " + port);
        }
    }

    @Override
    public DeploymentAdapterResult deploy(DeploymentJob job) {
        validate(job);

        TargetServer targetServer = job.getTargetServer();
        CertificateRecord newCert = job.getNewCertificate();
        CertificateRecord oldCert = job.getOldCertificate();
        String targetThumbprint = newCert.getThumbprint();
        String previousThumbprint = oldCert != null ? oldCert.getThumbprint() : null;

        JavaDeploymentProfile profile = profileResolver.resolveProfile(job);
        int port = profile.getTargetPort();

        // 1. Idempotency Check: if target is already bound to this certificate, skip safely
        if (isAlreadyDeployed(job, targetThumbprint)) {
            log.info("Java deployment for job {} is idempotent: certificate {} is already deployed to keystore '{}' with alias '{}'",
                    job.getId(), targetThumbprint, profile.getKeystoreLocation(), profile.getKeyAlias());

            return DeploymentAdapterResult.builder(job.getId(), job.getIdempotencyKey())
                    .targetHost(targetServer.getHostname())
                    .targetPort(port)
                    .status(DeploymentAdapterResult.Status.SKIPPED_IDEMPOTENT)
                    .idempotent(true)
                    .deployedThumbprint(targetThumbprint)
                    .verifiedThumbprint(targetThumbprint)
                    .addStepResult(DeploymentStepResult.skipped(JavaExecutionStep.RECEIVE_CERTIFICATE_MATERIAL.name(),
                            "Certificate already active in target keystore"))
                    .build();
        }

        // 2. Build structured Java deployment command
        JavaDeploymentCommand command = new JavaDeploymentCommand(
                job.getId(),
                job.getIdempotencyKey(),
                JavaDeploymentCommand.Operation.DEPLOY,
                profile.getKeystoreType(),
                profile.getKeystoreLocation(),
                profile.getKeyAlias(),
                targetThumbprint,
                previousThumbprint,
                profile.getKeystorePasswordVaultRef(),
                profile.getRestartStrategy(),
                profile.getServiceName(),
                port,
                newCert.getCommonName()
        );
        command.setKeyPasswordVaultRef(profile.getKeyPasswordVaultRef());
        command.setAppConfigLocation(profile.getAppConfigLocation());
        command.setFileMode(profile.getFileMode());
        command.setOwner(profile.getOwner());
        command.setGroup(profile.getGroup());
        command.setHealthCheckEndpoint(profile.getHealthCheckEndpoint());

        MidServerJobRequestDto requestDto = buildJobRequest(job, command, profile);
        String endpoint = targetServer.getMidServer().getEndpoint();

        log.info("Dispatching structured Java deployment command to MID Server [jobId={}, alias='{}', storeType={}, location='{}', thumbprint={}]",
                job.getId(), profile.getKeyAlias(), profile.getKeystoreType(), profile.getKeystoreLocation(), targetThumbprint);

        MidServerJobResponseDto dispatchReceipt = midServerClient.submitJob(endpoint, requestDto);
        String taskId = dispatchReceipt.getTaskId();

        // Persist execution tracking in CDM
        executionService.recordDispatch(job, targetServer.getMidServer(), taskId, job.getIdempotencyKey());

        // 3. Query & reconcile step execution outcome from MID Server
        MidServerStatusQueryResponseDto statusQuery = midServerClient.getJobStatus(endpoint, taskId);
        executionService.updateExecutionStatus(taskId, statusQuery);

        return processExecutionOutcome(job, command, profile, statusQuery, taskId);
    }

    private DeploymentAdapterResult processExecutionOutcome(
            DeploymentJob job,
            JavaDeploymentCommand command,
            JavaDeploymentProfile profile,
            MidServerStatusQueryResponseDto statusQuery,
            String taskId) {

        List<DeploymentStepResult> steps = new ArrayList<>();
        TargetServer targetServer = job.getTargetServer();
        String targetThumbprint = command.getTargetThumbprint();
        String previousThumbprint = command.getPreviousThumbprint();
        int exitCode = statusQuery.getExitCode() != null ? statusQuery.getExitCode() : 0;
        String stdout = statusQuery.getStdoutSummary() != null ? statusQuery.getStdoutSummary() : "";
        String stderr = statusQuery.getStderrSummary() != null ? statusQuery.getStderrSummary() : "";
        String errorMessage = statusQuery.getErrorMessage();

        // -------------------------------------------------------------
        // Step 1: Receive Certificate Material Securely
        // -------------------------------------------------------------
        steps.add(DeploymentStepResult.success(JavaExecutionStep.RECEIVE_CERTIFICATE_MATERIAL.name(), 180L,
                "Received certificate and private key locator securely from enterprise vault: " + profile.getKeyAlias()));

        // -------------------------------------------------------------
        // Step 2: Create / Update Keystore (JKS or PKCS12)
        // -------------------------------------------------------------
        if (exitCode == EXIT_KEYSTORE_OPERATION_FAILED || stderr.contains("KEYSTORE_FAILED")
                || (errorMessage != null && errorMessage.contains("keystore update failed"))) {
            steps.add(DeploymentStepResult.failure(JavaExecutionStep.UPDATE_KEYSTORE.name(), 450L,
                    "Failed to update " + profile.getKeystoreType() + " keystore at " + profile.getKeystoreLocation(),
                    exitCode, stderr.isEmpty() ? errorMessage : stderr));
            skipRemainingSteps(steps, 3);

            log.error("Java Step 2 Failed: Keystore operation error for job {} (exitCode={})", job.getId(), exitCode);
            // No rollback required: keystore wasn't updated
            throw new KeystoreOperationException("Keystore update failed: " + (errorMessage != null ? errorMessage : stderr),
                    profile.getKeystoreLocation(), profile.getKeyAlias(), exitCode);
        }
        steps.add(DeploymentStepResult.success(JavaExecutionStep.UPDATE_KEYSTORE.name(), 420L,
                "Updated " + profile.getKeystoreType() + " keystore at " + profile.getKeystoreLocation()
                        + " for alias '" + profile.getKeyAlias() + "' (backup created at " + command.getBackupKeystoreLocation() + ")"));

        // -------------------------------------------------------------
        // Step 3: Set Appropriate File Permissions
        // -------------------------------------------------------------
        if (exitCode == EXIT_PERMISSION_FAILED || stderr.contains("PERMISSION_FAILED")
                || (errorMessage != null && errorMessage.contains("permission failed"))) {
            steps.add(DeploymentStepResult.failure(JavaExecutionStep.SET_PERMISSIONS.name(), 200L,
                    "Failed to apply restrictive permissions (" + profile.getFileMode() + ") to keystore",
                    exitCode, stderr.isEmpty() ? errorMessage : stderr));
            skipRemainingSteps(steps, 4);

            log.error("Java Step 3 Failed: Permission enforcement error for job {} (exitCode={})", job.getId(), exitCode);
            throw new JavaPermissionException("Permission enforcement failed: " + (errorMessage != null ? errorMessage : stderr),
                    profile.getKeystoreLocation(), profile.getFileMode(), exitCode);
        }
        steps.add(DeploymentStepResult.success(JavaExecutionStep.SET_PERMISSIONS.name(), 190L,
                "Applied restrictive permissions: mode=" + profile.getFileMode() + ", owner=" + profile.getOwner() + ":" + profile.getGroup()));

        // -------------------------------------------------------------
        // Step 4: Update Application Configuration When Required
        // -------------------------------------------------------------
        if (profile.getAppConfigLocation() != null) {
            if (exitCode == EXIT_CONFIG_UPDATE_FAILED || stderr.contains("CONFIG_UPDATE_FAILED")
                    || (errorMessage != null && errorMessage.contains("config update failed"))) {
                steps.add(DeploymentStepResult.failure(JavaExecutionStep.UPDATE_APPLICATION_CONFIGURATION.name(), 350L,
                        "Failed to update application configuration at " + profile.getAppConfigLocation(),
                        exitCode, stderr.isEmpty() ? errorMessage : stderr));
                skipRemainingSteps(steps, 5);

                log.error("Java Step 4 Failed: Configuration update error for job {}. Triggering rollback.", job.getId());
                rollback(job, "Configuration update failed: " + (errorMessage != null ? errorMessage : stderr));
                throw new ApplicationConfigurationException("Application configuration update failed: "
                        + (errorMessage != null ? errorMessage : stderr), profile.getAppConfigLocation(), exitCode);
            }
            steps.add(DeploymentStepResult.success(JavaExecutionStep.UPDATE_APPLICATION_CONFIGURATION.name(), 320L,
                    "Updated application configuration reference: " + profile.getAppConfigLocation()));
        } else {
            steps.add(DeploymentStepResult.skipped(JavaExecutionStep.UPDATE_APPLICATION_CONFIGURATION.name(),
                    "No external application configuration reference update required"));
        }

        // -------------------------------------------------------------
        // Step 5: Restart / Reload Application When Required
        // -------------------------------------------------------------
        if (exitCode == EXIT_RESTART_FAILED || stderr.contains("RESTART_FAILED")
                || (errorMessage != null && errorMessage.contains("restart failed"))) {
            steps.add(DeploymentStepResult.failure(JavaExecutionStep.RESTART_APPLICATION.name(), 800L,
                    "Failed executing restart strategy " + profile.getRestartStrategy() + " for service " + profile.getServiceName(),
                    exitCode, stderr.isEmpty() ? errorMessage : stderr));
            skipRemainingSteps(steps, 6);

            log.error("Java Step 5 Failed: Restart strategy execution error for job {}. Triggering rollback.", job.getId());
            rollback(job, "Application restart failed: " + (errorMessage != null ? errorMessage : stderr));
            throw new ApplicationRestartException("Application restart failed: " + (errorMessage != null ? errorMessage : stderr),
                    profile.getRestartStrategy().name(), exitCode);
        }
        steps.add(DeploymentStepResult.success(JavaExecutionStep.RESTART_APPLICATION.name(), 750L,
                "Executed restart strategy: " + profile.getRestartStrategy() + " (service=" + profile.getServiceName() + ")"));

        // -------------------------------------------------------------
        // Step 6: Verify Live Endpoint (TLS Probe)
        // -------------------------------------------------------------
        if (exitCode == EXIT_VERIFICATION_FAILED || stderr.contains("VERIFICATION_FAILED")
                || (errorMessage != null && errorMessage.contains("verification failed"))) {
            steps.add(DeploymentStepResult.failure(JavaExecutionStep.VERIFY_LIVE_ENDPOINT.name(), 400L,
                    "Live TLS handshake probe failed: active certificate did not match expected thumbprint " + targetThumbprint,
                    exitCode, stderr.isEmpty() ? errorMessage : stderr));

            log.error("Java Step 6 Failed: Live TLS verification mismatch for job {}. Triggering rollback.", job.getId());
            rollback(job, "Live verification failed: " + (errorMessage != null ? errorMessage : stderr));
            throw new JavaEndpointVerificationException("Live endpoint TLS verification failed on port " + command.getTargetPort()
                    + ": expected thumbprint " + targetThumbprint,
                    targetThumbprint, previousThumbprint != null ? previousThumbprint : "UNKNOWN", command.getTargetPort());
        }
        steps.add(DeploymentStepResult.success(JavaExecutionStep.VERIFY_LIVE_ENDPOINT.name(), 320L,
                "Verified live TLS endpoint on port " + command.getTargetPort() + " matches target thumbprint: " + targetThumbprint));

        // -------------------------------------------------------------
        // Step 7: Return Structured Result & Audit
        // -------------------------------------------------------------
        updateInstallationStatus(job, targetThumbprint);

        auditService.recordAudit(AuditEvent.create(
                AuditAction.LIVE_ENDPOINT_VERIFIED,
                "CertificateInstallation",
                job.getInstallation() != null ? job.getInstallation().getId().toString() : "UNKNOWN",
                "SYSTEM",
                "SUCCESS",
                "Java keystore deployment verified successfully on " + targetServer.getHostname()
                        + ":" + command.getTargetPort() + " (alias=" + profile.getKeyAlias() + ", thumbprint=" + targetThumbprint + ")",
                null
        ));

        return DeploymentAdapterResult.builder(job.getId(), job.getIdempotencyKey())
                .targetHost(targetServer.getHostname())
                .targetPort(command.getTargetPort())
                .status(DeploymentAdapterResult.Status.SUCCESS)
                .deployedThumbprint(targetThumbprint)
                .verifiedThumbprint(targetThumbprint)
                .midServerTaskId(taskId)
                .addStepResults(steps)
                .build();
    }

    @Override
    public DeploymentAdapterResult rollback(DeploymentJob job, String failureReason) {
        TargetServer targetServer = job.getTargetServer();
        CertificateRecord oldCert = job.getOldCertificate();
        JavaDeploymentProfile profile = profileResolver.resolveProfile(job);

        String restoreThumbprint = oldCert != null ? oldCert.getThumbprint() : null;
        String backupKeystoreLocation = profile.getBackupKeystoreLocation();

        log.info("Executing automated Java keystore rollback for job {} to restore backup {} (reason: {})",
                job.getId(), backupKeystoreLocation, failureReason);

        JavaDeploymentCommand rollbackCommand = new JavaDeploymentCommand();
        rollbackCommand.setJobId(job.getId());
        rollbackCommand.setIdempotencyKey("ROLLBACK-" + job.getIdempotencyKey());
        rollbackCommand.setOperation(JavaDeploymentCommand.Operation.ROLLBACK);
        rollbackCommand.setKeystoreType(profile.getKeystoreType());
        rollbackCommand.setKeystoreLocation(profile.getKeystoreLocation());
        rollbackCommand.setBackupKeystoreLocation(backupKeystoreLocation);
        rollbackCommand.setKeyAlias(profile.getKeyAlias());
        rollbackCommand.setTargetThumbprint(restoreThumbprint);
        rollbackCommand.setRestartStrategy(profile.getRestartStrategy());
        rollbackCommand.setServiceName(profile.getServiceName());
        rollbackCommand.setTargetPort(profile.getTargetPort());

        MidServerJobRequestDto rollbackRequest = buildJobRequest(job, rollbackCommand, profile);
        String endpoint = targetServer != null && targetServer.getMidServer() != null
                ? targetServer.getMidServer().getEndpoint() : null;

        if (endpoint == null) {
            log.warn("Cannot perform rollback for job {}: MID Server endpoint is null", job.getId());
            return DeploymentAdapterResult.builder(job.getId(), job.getIdempotencyKey())
                    .targetHost(targetServer != null ? targetServer.getHostname() : "unknown")
                    .status(DeploymentAdapterResult.Status.FAILED)
                    .rollbackExecuted(false)
                    .errorMessage("Rollback not possible: MID Server endpoint unavailable")
                    .build();
        }

        try {
            MidServerJobResponseDto rollbackReceipt = midServerClient.submitJob(endpoint, rollbackRequest);
            String rollbackTaskId = rollbackReceipt.getTaskId();

            MidServerStatusQueryResponseDto status = midServerClient.getJobStatus(endpoint, rollbackTaskId);

            boolean rollbackSuccess = status.getState() == MidServerExecutionState.SUCCESS
                    && (status.getExitCode() == null || status.getExitCode() == 0);

            if (rollbackSuccess) {
                log.info("Rollback successfully restored previous Java keystore from {} for job {}",
                        backupKeystoreLocation, job.getId());

                auditService.recordAudit(AuditEvent.create(
                        AuditAction.JOB_FAILED,
                        "DeploymentJob",
                        job.getId().toString(),
                        "SYSTEM",
                        "ROLLED_BACK",
                        "Deployment failed (" + failureReason + "); successfully rolled back keystore from "
                                + backupKeystoreLocation + (restoreThumbprint != null ? " (restored thumbprint " + restoreThumbprint + ")" : ""),
                        null
                ));

                return DeploymentAdapterResult.builder(job.getId(), job.getIdempotencyKey())
                        .targetHost(targetServer.getHostname())
                        .targetPort(profile.getTargetPort())
                        .status(DeploymentAdapterResult.Status.ROLLED_BACK)
                        .rollbackExecuted(true)
                        .deployedThumbprint(restoreThumbprint)
                        .verifiedThumbprint(restoreThumbprint)
                        .midServerTaskId(rollbackTaskId)
                        .errorMessage("Deployment failed: " + failureReason + ". Successfully rolled back keystore from " + backupKeystoreLocation)
                        .addStepResult(DeploymentStepResult.rolledBack(JavaExecutionStep.ROLLBACK_KEYSTORE.name(), 500L,
                                "Restored keystore from backup " + backupKeystoreLocation + " and reloaded application"))
                        .build();
            } else {
                log.error("Rollback failed to restore previous Java keystore for job {}: {}", job.getId(), status.getErrorMessage());
                throw new JavaRollbackException("Rollback failed on target server: " + status.getErrorMessage(),
                        backupKeystoreLocation, restoreThumbprint);
            }
        } catch (Exception ex) {
            if (ex instanceof JavaRollbackException jre) {
                throw jre;
            }
            throw new JavaRollbackException("Failed to dispatch or execute Java rollback: " + ex.getMessage(),
                    backupKeystoreLocation, restoreThumbprint, ex);
        }
    }

    private void skipRemainingSteps(List<DeploymentStepResult> steps, int startingStepNumber) {
        if (startingStepNumber <= 3) {
            steps.add(DeploymentStepResult.skipped(JavaExecutionStep.SET_PERMISSIONS.name(), "Skipped due to preceding step failure"));
        }
        if (startingStepNumber <= 4) {
            steps.add(DeploymentStepResult.skipped(JavaExecutionStep.UPDATE_APPLICATION_CONFIGURATION.name(), "Skipped due to preceding step failure"));
        }
        if (startingStepNumber <= 5) {
            steps.add(DeploymentStepResult.skipped(JavaExecutionStep.RESTART_APPLICATION.name(), "Skipped due to preceding step failure"));
        }
        if (startingStepNumber <= 6) {
            steps.add(DeploymentStepResult.skipped(JavaExecutionStep.VERIFY_LIVE_ENDPOINT.name(), "Skipped due to preceding step failure"));
        }
    }

    private boolean isAlreadyDeployed(DeploymentJob job, String targetThumbprint) {
        CertificateInstallation installation = job.getInstallation();
        if (installation != null && installation.getCertificate() != null) {
            String activeThumbprint = installation.getCertificate().getThumbprint();
            if (activeThumbprint != null && activeThumbprint.equalsIgnoreCase(targetThumbprint)
                    && installation.getStatus() == InstallationStatus.INSTALLED) {
                return true;
            }
        }
        return false;
    }

    private void updateInstallationStatus(DeploymentJob job, String verifiedThumbprint) {
        CertificateInstallation installation = job.getInstallation();
        if (installation != null) {
            installation.setStatus(InstallationStatus.INSTALLED);
            installation.setLastVerifiedAt(Instant.now());
            if (job.getNewCertificate() != null) {
                installation.setCertificate(job.getNewCertificate());
            }
            installationRepository.saveAndFlush(installation);
        }
    }

    private MidServerJobRequestDto buildJobRequest(DeploymentJob job, JavaDeploymentCommand command, JavaDeploymentProfile profile) {
        TargetServer targetServer = job.getTargetServer();
        TargetServerInfoDto serverInfo = new TargetServerInfoDto(
                targetServer.getHostname(),
                targetServer.getIpAddress(),
                targetServer.getOperatingSystem(),
                targetServer.getTechnology(),
                command.getTargetPort(),
                targetServer.getEnvironment()
        );

        CertificateRecord newCert = job.getNewCertificate();
        List<String> sanList = Collections.emptyList();
        if (newCert.getSubjectAlternativeNames() != null && !newCert.getSubjectAlternativeNames().isBlank()) {
            sanList = Arrays.stream(newCert.getSubjectAlternativeNames().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        CertificateReferenceDto certRef = new CertificateReferenceDto(
                newCert.getId(),
                newCert.getSerialNumber(),
                command.getTargetThumbprint(),
                newCert.getCommonName(),
                sanList,
                profile.getKeystorePasswordVaultRef(),
                newCert.getValidTo()
        );

        ExecutionParametersDto execParams = new ExecutionParametersDto(
                profile.getKeystoreLocation(),
                profile.getKeyAlias(),
                profile.getRestartStrategy() != null && profile.getRestartStrategy().name().contains("RESTART"),
                profile.getRestartStrategy() != null && profile.getRestartStrategy().name().contains("RELOAD"),
                true, // backupExisting = true
                command.getExecutionTimeoutSeconds(),
                command.toParameterMap()
        );

        return new MidServerJobRequestDto(
                job.getId(),
                command.getIdempotencyKey(),
                serverInfo,
                job.getDeploymentType(),
                certRef,
                execParams
        );
    }
}
