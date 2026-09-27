package com.grassroots.cdm.deployment.adapter.linux;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.adapter.DeploymentAdapter;
import com.grassroots.cdm.deployment.adapter.DeploymentAdapterResult;
import com.grassroots.cdm.deployment.adapter.DeploymentStepResult;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxCertificateTransferException;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxConfigurationUpdateException;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxConfigurationValidationException;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxPermissionException;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxPrivateKeyTransferException;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxRollbackException;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxServiceReloadException;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxValidationException;
import com.grassroots.cdm.deployment.adapter.linux.exception.LinuxVerificationException;
import com.grassroots.cdm.deployment.adapter.linux.model.LinuxDeploymentCommand;
import com.grassroots.cdm.deployment.adapter.linux.model.LinuxExecutionStep;
import com.grassroots.cdm.deployment.adapter.linux.model.LinuxFilePermissions;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.InstallationStatus;
import com.grassroots.cdm.entity.enums.MidServerExecutionState;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Abstract base deployment adapter for Linux web servers (Apache and Nginx).
 *
 * Implements the 8 conceptual steps:
 * 1. Transfer certificate securely
 * 2. Transfer private key securely (vault locator, zero key exposure in logs)
 * 3. Apply restrictive permissions (enforce secure POSIX modes: 0644 for certs, strictly 0600/0640 for keys)
 * 4. Validate configuration (e.g. apache2ctl -t, nginx -t)
 * 5. Update certificate configuration (manage directives, create backup config for rollback)
 * 6. Gracefully reload service (e.g. systemctl reload apache2 / nginx)
 * 7. Verify live endpoint (TLS probe on target port verifying target thumbprint)
 * 8. Return structured deployment result with sub-step telemetry and automated rollback
 *
 * Commands and configuration are strictly generated from validated deployment configurations,
 * preventing arbitrary user command execution or shell injection.
 */
public abstract class AbstractLinuxDeploymentAdapter implements DeploymentAdapter {

    private static final Logger log = LoggerFactory.getLogger(AbstractLinuxDeploymentAdapter.class);

    // Standardized Linux MID Server exit codes
    public static final int EXIT_CERT_TRANSFER_FAILED = 201;
    public static final int EXIT_KEY_TRANSFER_FAILED = 202;
    public static final int EXIT_PERMISSIONS_FAILED = 203;
    public static final int EXIT_CONFIG_VALIDATION_FAILED = 204;
    public static final int EXIT_CONFIG_UPDATE_FAILED = 205;
    public static final int EXIT_SERVICE_RELOAD_FAILED = 206;
    public static final int EXIT_VERIFICATION_FAILED = 207;

    private static final Pattern SAFE_UNIX_PATH = Pattern.compile("^/[a-zA-Z0-9_./\\-]+$");

    protected final MidServerClient midServerClient;
    protected final MidServerExecutionService executionService;
    protected final AuditService auditService;
    protected final DeploymentJobRepository deploymentJobRepository;
    protected final CertificateInstallationRepository installationRepository;

    protected AbstractLinuxDeploymentAdapter(
            MidServerClient midServerClient,
            MidServerExecutionService executionService,
            AuditService auditService,
            DeploymentJobRepository deploymentJobRepository,
            CertificateInstallationRepository installationRepository) {
        this.midServerClient = midServerClient;
        this.executionService = executionService;
        this.auditService = auditService;
        this.deploymentJobRepository = deploymentJobRepository;
        this.installationRepository = installationRepository;
    }

    // Technology-specific template methods implemented by Apache and Nginx adapters
    public abstract String getDefaultConfigPath(TargetServer targetServer, String siteName);
    public abstract String getDefaultCertificatePath(CertificateRecord cert);
    public abstract String getDefaultPrivateKeyPath(CertificateRecord cert);
    public abstract LinuxFilePermissions getDefaultCertificatePermissions();
    public abstract LinuxFilePermissions getDefaultPrivateKeyPermissions(TargetServer targetServer);
    public abstract String getConfigValidationCommand(TargetServer targetServer);
    public abstract String getServiceReloadCommand(TargetServer targetServer);

    @Override
    public boolean supports(DeploymentJob job) {
        if (job == null) return false;
        if (job.getTargetServer() != null && job.getTargetServer().getTechnology() == getSupportedTechnology()) {
            return true;
        }
        return getSupportedTargetType().name().equalsIgnoreCase(job.getTargetType());
    }

    @Override
    public void validate(DeploymentJob job) {
        if (job == null) {
            throw new LinuxValidationException("DeploymentJob cannot be null");
        }

        TargetServer targetServer = job.getTargetServer();
        if (targetServer == null) {
            throw new LinuxValidationException("Target server is required for " + getSupportedTechnology() + " deployment");
        }

        if (!isLinuxOperatingSystem(targetServer.getOperatingSystem())) {
            throw new LinuxValidationException("Target server operating system must be a supported Linux distribution for "
                    + getSupportedTechnology() + " deployment, but was: " + targetServer.getOperatingSystem());
        }

        if (targetServer.getTechnology() != getSupportedTechnology()) {
            throw new LinuxValidationException("Target server technology must be " + getSupportedTechnology()
                    + ", but was: " + targetServer.getTechnology());
        }

        int port = job.getTargetPort();
        if (port <= 0 || port > 65535) {
            throw new LinuxValidationException("Invalid target port for " + getSupportedTechnology() + " binding: " + port);
        }

        CertificateRecord newCert = job.getNewCertificate();
        if (newCert == null) {
            throw new LinuxValidationException("New certificate is required for " + getSupportedTechnology() + " deployment");
        }
        if (newCert.getThumbprint() == null || newCert.getThumbprint().isBlank()) {
            throw new LinuxValidationException("New certificate must have a valid SHA-1/SHA-256 thumbprint");
        }
        if (newCert.getValidTo() != null && newCert.getValidTo().isBefore(Instant.now())) {
            throw new LinuxValidationException("New certificate is already expired (validTo: " + newCert.getValidTo() + ")");
        }

        MidServer midServer = targetServer.getMidServer();
        if (midServer == null) {
            throw new LinuxValidationException("Target server " + targetServer.getHostname() + " has no assigned MID Server");
        }
        if (midServer.getStatus() != MidServerStatus.UP) {
            throw new LinuxValidationException("Assigned MID Server " + midServer.getName() + " is not UP (current status: " + midServer.getStatus() + ")");
        }

        // Security validation: check path format and prohibit path traversal
        String siteName = resolveSiteName(job);
        String configPath = resolveConfigPath(job, siteName);
        String certPath = resolveCertificatePath(job, newCert);
        String keyPath = resolvePrivateKeyPath(job, newCert);

        validateSafeUnixPath("configFilePath", configPath);
        validateSafeUnixPath("certificatePath", certPath);
        validateSafeUnixPath("privateKeyPath", keyPath);

        // Security validation: enforce restrictive permissions
        LinuxFilePermissions keyPerms = getDefaultPrivateKeyPermissions(targetServer);
        if (!keyPerms.isSecureForPrivateKey()) {
            throw new LinuxPermissionException("Insecure private key permissions rejected: " + keyPerms.fileMode()
                    + ". Private keys must be mode 0600 or 0640.", keyPath, keyPerms.fileMode(), EXIT_PERMISSIONS_FAILED);
        }

        LinuxFilePermissions certPerms = getDefaultCertificatePermissions();
        if (!certPerms.isSecureForCertificate()) {
            throw new LinuxPermissionException("Insecure certificate permissions rejected: " + certPerms.fileMode()
                    + ". Certificates must be mode 0644, 0640, or 0600.", certPath, certPerms.fileMode(), EXIT_PERMISSIONS_FAILED);
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
        int port = job.getTargetPort();
        String siteName = resolveSiteName(job);

        // 1. Idempotency Check: if target installation is already bound to this certificate, skip safely
        if (isAlreadyDeployed(job, targetThumbprint)) {
            log.info("{} deployment for job {} is idempotent: certificate {} is already deployed to site '{}' on port {}",
                    getSupportedTechnology(), job.getId(), targetThumbprint, siteName, port);

            return DeploymentAdapterResult.builder(job.getId(), job.getIdempotencyKey())
                    .targetHost(targetServer.getHostname())
                    .targetPort(port)
                    .status(DeploymentAdapterResult.Status.SKIPPED_IDEMPOTENT)
                    .idempotent(true)
                    .deployedThumbprint(targetThumbprint)
                    .verifiedThumbprint(targetThumbprint)
                    .addStepResult(DeploymentStepResult.skipped(LinuxExecutionStep.VALIDATE_PREREQUISITES.name(), "Certificate already active on target"))
                    .build();
        }

        // 2. Build structured Linux deployment command (strictly typed, zero shell injection)
        String configFilePath = resolveConfigPath(job, siteName);
        String certificatePath = resolveCertificatePath(job, newCert);
        String privateKeyPath = resolvePrivateKeyPath(job, newCert);
        String validationCmd = getConfigValidationCommand(targetServer);
        String reloadCmd = getServiceReloadCommand(targetServer);
        String vaultRef = "cyberark://GrassrootsSafe/Account/" + targetThumbprint;

        LinuxDeploymentCommand command = new LinuxDeploymentCommand(
                job.getId(),
                job.getIdempotencyKey(),
                LinuxDeploymentCommand.Operation.DEPLOY,
                getSupportedTechnology(),
                configFilePath,
                certificatePath,
                privateKeyPath,
                targetThumbprint,
                previousThumbprint,
                vaultRef,
                validationCmd,
                reloadCmd,
                port,
                newCert.getCommonName()
        );
        command.setCertificatePermissions(getDefaultCertificatePermissions());
        command.setPrivateKeyPermissions(getDefaultPrivateKeyPermissions(targetServer));

        MidServerJobRequestDto requestDto = buildJobRequest(job, command);
        String endpoint = targetServer.getMidServer().getEndpoint();

        log.info("Dispatching structured {} deployment command to MID Server [jobId={}, site='{}', port={}, thumbprint={}]",
                getSupportedTechnology(), job.getId(), siteName, port, targetThumbprint);

        MidServerJobResponseDto dispatchReceipt = midServerClient.submitJob(endpoint, requestDto);
        String taskId = dispatchReceipt.getTaskId();

        // Persist execution tracking in CDM
        executionService.recordDispatch(job, targetServer.getMidServer(), taskId, job.getIdempotencyKey());

        // 3. Query & reconcile step execution outcome from MID Server
        MidServerStatusQueryResponseDto statusQuery = midServerClient.getJobStatus(endpoint, taskId);
        executionService.updateExecutionStatus(taskId, statusQuery);

        return processExecutionOutcome(job, command, statusQuery, taskId);
    }

    private DeploymentAdapterResult processExecutionOutcome(
            DeploymentJob job,
            LinuxDeploymentCommand command,
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

        // Prerequisites passed
        steps.add(DeploymentStepResult.success(LinuxExecutionStep.VALIDATE_PREREQUISITES.name(), 120L,
                "Prerequisites validated successfully: OS, technology, paths, and secure permission boundaries"));

        // -------------------------------------------------------------
        // Step 1: Transfer Certificate Securely
        // -------------------------------------------------------------
        if (exitCode == EXIT_CERT_TRANSFER_FAILED || stderr.contains("CERT_TRANSFER_FAILED")
                || (errorMessage != null && errorMessage.contains("certificate transfer failed"))) {
            steps.add(DeploymentStepResult.failure(LinuxExecutionStep.TRANSFER_CERTIFICATE.name(), 400L,
                    "Failed to transfer certificate to " + command.getCertificatePath(), exitCode, stderr.isEmpty() ? errorMessage : stderr));
            skipRemainingSteps(steps, 2);

            log.error("{} Step 1 Failed: Certificate transfer error for job {} (exitCode={})", getSupportedTechnology(), job.getId(), exitCode);
            // No rollback required: config not touched
            throw new LinuxCertificateTransferException("Certificate transfer failed: " + (errorMessage != null ? errorMessage : stderr),
                    command.getCertificatePath(), exitCode);
        }
        steps.add(DeploymentStepResult.success(LinuxExecutionStep.TRANSFER_CERTIFICATE.name(), 350L,
                "Transferred certificate to " + command.getCertificatePath()));

        // -------------------------------------------------------------
        // Step 2: Transfer Private Key Securely (Zero key exposure in logs)
        // -------------------------------------------------------------
        if (exitCode == EXIT_KEY_TRANSFER_FAILED || stderr.contains("KEY_TRANSFER_FAILED")
                || (errorMessage != null && errorMessage.contains("private key transfer failed"))) {
            steps.add(DeploymentStepResult.failure(LinuxExecutionStep.TRANSFER_PRIVATE_KEY.name(), 450L,
                    "Failed to transfer private key to " + command.getPrivateKeyPath(), exitCode, stderr.isEmpty() ? errorMessage : stderr));
            skipRemainingSteps(steps, 3);

            log.error("{} Step 2 Failed: Private key transfer error for job {} (exitCode={})", getSupportedTechnology(), job.getId(), exitCode);
            // No rollback required: config not touched
            throw new LinuxPrivateKeyTransferException("Private key transfer failed: " + (errorMessage != null ? errorMessage : stderr),
                    command.getPrivateKeyPath(), exitCode);
        }
        steps.add(DeploymentStepResult.success(LinuxExecutionStep.TRANSFER_PRIVATE_KEY.name(), 420L,
                "Transferred private key securely from vault reference to " + command.getPrivateKeyPath()));

        // -------------------------------------------------------------
        // Step 3: Apply Restrictive POSIX Permissions
        // -------------------------------------------------------------
        if (exitCode == EXIT_PERMISSIONS_FAILED || stderr.contains("PERMISSIONS_FAILED")
                || (errorMessage != null && errorMessage.contains("permission failed"))) {
            steps.add(DeploymentStepResult.failure(LinuxExecutionStep.APPLY_RESTRICTIVE_PERMISSIONS.name(), 250L,
                    "Failed to apply restrictive POSIX permissions (" + command.getPrivateKeyPermissions().fileMode() + ") to private key",
                    exitCode, stderr.isEmpty() ? errorMessage : stderr));
            skipRemainingSteps(steps, 4);

            log.error("{} Step 3 Failed: Permission enforcement error for job {} (exitCode={})", getSupportedTechnology(), job.getId(), exitCode);
            // No rollback required: config not touched
            throw new LinuxPermissionException("Permission enforcement failed on " + command.getPrivateKeyPath() + ": "
                    + (errorMessage != null ? errorMessage : stderr), command.getPrivateKeyPath(), command.getPrivateKeyPermissions().fileMode(), exitCode);
        }
        steps.add(DeploymentStepResult.success(LinuxExecutionStep.APPLY_RESTRICTIVE_PERMISSIONS.name(), 210L,
                "Applied restrictive POSIX permissions: cert=" + command.getCertificatePermissions().fileMode()
                        + ", key=" + command.getPrivateKeyPermissions().fileMode() + " (owner=" + command.getPrivateKeyPermissions().owner() + ")"));

        // -------------------------------------------------------------
        // Step 4: Validate Configuration Syntax Pre-Update
        // -------------------------------------------------------------
        if (exitCode == EXIT_CONFIG_VALIDATION_FAILED || stderr.contains("CONFIG_VALIDATION_FAILED")
                || (errorMessage != null && errorMessage.contains("configuration validation failed"))) {
            steps.add(DeploymentStepResult.failure(LinuxExecutionStep.VALIDATE_CONFIGURATION.name(), 500L,
                    "Web server configuration syntax validation failed: " + command.getConfigValidationCommand(),
                    exitCode, stderr.isEmpty() ? errorMessage : stderr));
            skipRemainingSteps(steps, 5);

            log.error("{} Step 4 Failed: Configuration validation syntax error for job {} (exitCode={})", getSupportedTechnology(), job.getId(), exitCode);
            // No rollback required: existing active config untouched
            throw new LinuxConfigurationValidationException("Configuration syntax validation failed: "
                    + (errorMessage != null ? errorMessage : stderr), command.getConfigValidationCommand(), stderr, exitCode);
        }
        steps.add(DeploymentStepResult.success(LinuxExecutionStep.VALIDATE_CONFIGURATION.name(), 480L,
                "Configuration syntax validation passed: " + command.getConfigValidationCommand()));

        // -------------------------------------------------------------
        // Step 5: Update Certificate Configuration Directives
        // -------------------------------------------------------------
        if (exitCode == EXIT_CONFIG_UPDATE_FAILED || stderr.contains("CONFIG_UPDATE_FAILED")
                || (errorMessage != null && errorMessage.contains("configuration update failed"))) {
            steps.add(DeploymentStepResult.failure(LinuxExecutionStep.UPDATE_CERTIFICATE_CONFIGURATION.name(), 600L,
                    "Failed to update certificate configuration directives in " + command.getConfigFilePath(),
                    exitCode, stderr.isEmpty() ? errorMessage : stderr));
            skipRemainingSteps(steps, 6);

            log.error("{} Step 5 Failed: Configuration update error for job {} (exitCode={}). Triggering rollback.", getSupportedTechnology(), job.getId(), exitCode);
            if (previousThumbprint != null && !previousThumbprint.isBlank()) {
                rollback(job, "Configuration update failed: " + (errorMessage != null ? errorMessage : stderr));
            }
            throw new LinuxConfigurationUpdateException("Failed to update certificate directives in " + command.getConfigFilePath()
                    + ": " + (errorMessage != null ? errorMessage : stderr), command.getConfigFilePath(), exitCode);
        }
        steps.add(DeploymentStepResult.success(LinuxExecutionStep.UPDATE_CERTIFICATE_CONFIGURATION.name(), 550L,
                "Updated certificate configuration directives in " + command.getConfigFilePath() + " (backup created at " + command.getPreviousConfigBackupPath() + ")"));

        // -------------------------------------------------------------
        // Step 6: Gracefully Reload Service
        // -------------------------------------------------------------
        if (exitCode == EXIT_SERVICE_RELOAD_FAILED || stderr.contains("SERVICE_RELOAD_FAILED")
                || (errorMessage != null && errorMessage.contains("reload failed"))) {
            steps.add(DeploymentStepResult.failure(LinuxExecutionStep.RELOAD_SERVICE.name(), 700L,
                    "Failed to gracefully reload service: " + command.getServiceReloadCommand(),
                    exitCode, stderr.isEmpty() ? errorMessage : stderr));
            skipRemainingSteps(steps, 7);

            log.error("{} Step 6 Failed: Service reload error for job {} (exitCode={}). Triggering rollback.", getSupportedTechnology(), job.getId(), exitCode);
            rollback(job, "Service reload failed: " + (errorMessage != null ? errorMessage : stderr));
            throw new LinuxServiceReloadException("Graceful service reload failed (" + command.getServiceReloadCommand()
                    + "): " + (errorMessage != null ? errorMessage : stderr), command.getServiceReloadCommand(), exitCode);
        }
        steps.add(DeploymentStepResult.success(LinuxExecutionStep.RELOAD_SERVICE.name(), 650L,
                "Gracefully reloaded service without dropping connections: " + command.getServiceReloadCommand()));

        // -------------------------------------------------------------
        // Step 7: Verify Live Endpoint (TLS Probe)
        // -------------------------------------------------------------
        if (exitCode == EXIT_VERIFICATION_FAILED || stderr.contains("VERIFICATION_FAILED")
                || (errorMessage != null && errorMessage.contains("verification failed"))) {
            steps.add(DeploymentStepResult.failure(LinuxExecutionStep.VERIFY_LIVE_ENDPOINT.name(), 400L,
                    "Live endpoint TLS verification failed: served certificate thumbprint did not match expected " + targetThumbprint,
                    exitCode, stderr.isEmpty() ? errorMessage : stderr));

            log.error("{} Step 7 Failed: Live endpoint verification mismatch for job {}. Triggering rollback.", getSupportedTechnology(), job.getId());
            rollback(job, "Live verification failed: " + (errorMessage != null ? errorMessage : stderr));
            throw new LinuxVerificationException("Live TLS endpoint verification failed on port " + command.getTargetPort()
                    + ": active certificate did not match expected thumbprint " + targetThumbprint,
                    targetThumbprint, previousThumbprint != null ? previousThumbprint : "UNKNOWN", command.getTargetPort());
        }
        steps.add(DeploymentStepResult.success(LinuxExecutionStep.VERIFY_LIVE_ENDPOINT.name(), 320L,
                "Verified live TLS handshake on port " + command.getTargetPort() + " matches target thumbprint: " + targetThumbprint));

        // -------------------------------------------------------------
        // Step 8: Return Structured Deployment Result
        // -------------------------------------------------------------
        updateInstallationStatus(job, targetThumbprint);

        auditService.recordAudit(AuditEvent.create(
                AuditAction.LIVE_ENDPOINT_VERIFIED,
                "CertificateInstallation",
                job.getInstallation() != null ? job.getInstallation().getId().toString() : "UNKNOWN",
                "SYSTEM",
                "SUCCESS",
                getSupportedTechnology() + " deployment verified successfully on " + targetServer.getHostname()
                        + ":" + command.getTargetPort() + " (thumbprint=" + targetThumbprint + ")",
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

        String siteName = resolveSiteName(job);
        String configFilePath = resolveConfigPath(job, siteName);
        String backupPath = configFilePath + ".cdm-bak";
        String restoreThumbprint = oldCert != null ? oldCert.getThumbprint() : null;

        log.info("Executing automated {} rollback for job {} to restore previous configuration backup {} (reason: {})",
                getSupportedTechnology(), job.getId(), backupPath, failureReason);

        LinuxDeploymentCommand rollbackCommand = new LinuxDeploymentCommand();
        rollbackCommand.setJobId(job.getId());
        rollbackCommand.setIdempotencyKey("ROLLBACK-" + job.getIdempotencyKey());
        rollbackCommand.setOperation(LinuxDeploymentCommand.Operation.ROLLBACK);
        rollbackCommand.setTechnology(getSupportedTechnology());
        rollbackCommand.setConfigFilePath(configFilePath);
        rollbackCommand.setPreviousConfigBackupPath(backupPath);
        rollbackCommand.setTargetThumbprint(restoreThumbprint);
        rollbackCommand.setConfigValidationCommand(getConfigValidationCommand(targetServer));
        rollbackCommand.setServiceReloadCommand(getServiceReloadCommand(targetServer));
        rollbackCommand.setTargetPort(job.getTargetPort());

        MidServerJobRequestDto rollbackRequest = buildJobRequest(job, rollbackCommand);
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
                log.info("Rollback successfully restored previous {} configuration from {} for job {}",
                        getSupportedTechnology(), backupPath, job.getId());

                auditService.recordAudit(AuditEvent.create(
                        AuditAction.JOB_FAILED,
                        "DeploymentJob",
                        job.getId().toString(),
                        "SYSTEM",
                        "ROLLED_BACK",
                        "Deployment failed (" + failureReason + "); successfully rolled back configuration from "
                                + backupPath + (restoreThumbprint != null ? " (restored thumbprint " + restoreThumbprint + ")" : ""),
                        null
                ));

                return DeploymentAdapterResult.builder(job.getId(), job.getIdempotencyKey())
                        .targetHost(targetServer.getHostname())
                .targetPort(job.getTargetPort())
                .status(DeploymentAdapterResult.Status.ROLLED_BACK)
                .rollbackExecuted(true)
                .deployedThumbprint(restoreThumbprint)
                .verifiedThumbprint(restoreThumbprint)
                .midServerTaskId(rollbackTaskId)
                .errorMessage("Deployment failed: " + failureReason + ". Successfully rolled back configuration from " + backupPath)
                .addStepResult(DeploymentStepResult.rolledBack(LinuxExecutionStep.ROLLBACK_CONFIGURATION.name(), 500L,
                        "Restored configuration from backup " + backupPath + " and reloaded service"))
                .build();
            } else {
                log.error("Rollback failed to restore previous {} configuration for job {}: {}",
                        getSupportedTechnology(), job.getId(), status.getErrorMessage());
                throw new LinuxRollbackException("Rollback failed on Linux server: " + status.getErrorMessage(),
                        backupPath, restoreThumbprint);
            }
        } catch (Exception ex) {
            if (ex instanceof LinuxRollbackException lre) {
                throw lre;
            }
            throw new LinuxRollbackException("Failed to dispatch or execute Linux rollback: " + ex.getMessage(),
                    backupPath, restoreThumbprint, ex);
        }
    }

    private void skipRemainingSteps(List<DeploymentStepResult> steps, int startingStepNumber) {
        if (startingStepNumber <= 2) {
            steps.add(DeploymentStepResult.skipped(LinuxExecutionStep.TRANSFER_PRIVATE_KEY.name(), "Skipped due to preceding step failure"));
        }
        if (startingStepNumber <= 3) {
            steps.add(DeploymentStepResult.skipped(LinuxExecutionStep.APPLY_RESTRICTIVE_PERMISSIONS.name(), "Skipped due to preceding step failure"));
        }
        if (startingStepNumber <= 4) {
            steps.add(DeploymentStepResult.skipped(LinuxExecutionStep.VALIDATE_CONFIGURATION.name(), "Skipped due to preceding step failure"));
        }
        if (startingStepNumber <= 5) {
            steps.add(DeploymentStepResult.skipped(LinuxExecutionStep.UPDATE_CERTIFICATE_CONFIGURATION.name(), "Skipped due to preceding step failure"));
        }
        if (startingStepNumber <= 6) {
            steps.add(DeploymentStepResult.skipped(LinuxExecutionStep.RELOAD_SERVICE.name(), "Skipped due to preceding step failure"));
        }
        if (startingStepNumber <= 7) {
            steps.add(DeploymentStepResult.skipped(LinuxExecutionStep.VERIFY_LIVE_ENDPOINT.name(), "Skipped due to preceding step failure"));
        }
    }

    protected boolean isAlreadyDeployed(DeploymentJob job, String targetThumbprint) {
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

    protected void updateInstallationStatus(DeploymentJob job, String verifiedThumbprint) {
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

    protected boolean isLinuxOperatingSystem(ServerOperatingSystem os) {
        if (os == null) return false;
        return os == ServerOperatingSystem.LINUX_RHEL
                || os == ServerOperatingSystem.LINUX_UBUNTU
                || os == ServerOperatingSystem.LINUX_CENTOS
                || os.name().startsWith("LINUX");
    }

    protected void validateSafeUnixPath(String fieldName, String path) {
        if (path == null || path.isBlank()) {
            throw new LinuxValidationException(fieldName + " cannot be null or empty");
        }
        if (!path.startsWith("/")) {
            throw new LinuxValidationException(fieldName + " must be an absolute Unix path starting with '/': " + path);
        }
        if (path.contains("..")) {
            throw new LinuxValidationException("Path traversal attempt ('..') detected in " + fieldName + ": " + path);
        }
        if (!SAFE_UNIX_PATH.matcher(path).matches()) {
            throw new LinuxValidationException("Invalid characters in " + fieldName + ": " + path);
        }
    }

    protected String resolveSiteName(DeploymentJob job) {
        if (job.getInstallation() != null && job.getInstallation().getBindingInfo() != null && !job.getInstallation().getBindingInfo().isBlank()) {
            return job.getInstallation().getBindingInfo().trim();
        }
        return "default";
    }

    protected String resolveConfigPath(DeploymentJob job, String siteName) {
        if (job.getInstallation() != null && job.getInstallation().getInstallationPath() != null
                && !job.getInstallation().getInstallationPath().isBlank()
                && job.getInstallation().getInstallationPath().endsWith(".conf")) {
            return job.getInstallation().getInstallationPath().trim();
        }
        return getDefaultConfigPath(job.getTargetServer(), siteName);
    }

    protected String resolveCertificatePath(DeploymentJob job, CertificateRecord cert) {
        if (job.getInstallation() != null && job.getInstallation().getInstallationPath() != null
                && !job.getInstallation().getInstallationPath().isBlank()
                && (job.getInstallation().getInstallationPath().endsWith(".crt") || job.getInstallation().getInstallationPath().endsWith(".pem"))) {
            return job.getInstallation().getInstallationPath().trim();
        }
        return getDefaultCertificatePath(cert);
    }

    protected String resolvePrivateKeyPath(DeploymentJob job, CertificateRecord cert) {
        return getDefaultPrivateKeyPath(cert);
    }

    protected MidServerJobRequestDto buildJobRequest(DeploymentJob job, LinuxDeploymentCommand command) {
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
                command.getVaultSecretReference(),
                newCert.getValidTo()
        );

        ExecutionParametersDto execParams = new ExecutionParametersDto(
                command.getConfigFilePath(),
                resolveSiteName(job),
                false, // Linux web servers gracefully reload configuration without dropping connections
                true,  // reloadConfig = true
                true,  // backupExisting = true
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
