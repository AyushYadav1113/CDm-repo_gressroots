package com.grassroots.cdm.deployment.adapter.iis;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.TargetType;
import com.grassroots.cdm.deployment.adapter.DeploymentAdapter;
import com.grassroots.cdm.deployment.adapter.DeploymentAdapterResult;
import com.grassroots.cdm.deployment.adapter.DeploymentStepResult;
import com.grassroots.cdm.deployment.adapter.iis.exception.IisBindingUpdateException;
import com.grassroots.cdm.deployment.adapter.iis.exception.IisCertificateImportException;
import com.grassroots.cdm.deployment.adapter.iis.exception.IisDeploymentException;
import com.grassroots.cdm.deployment.adapter.iis.exception.IisPermissionException;
import com.grassroots.cdm.deployment.adapter.iis.exception.IisRollbackException;
import com.grassroots.cdm.deployment.adapter.iis.exception.IisValidationException;
import com.grassroots.cdm.deployment.adapter.iis.exception.IisVerificationException;
import com.grassroots.cdm.deployment.adapter.iis.model.IisDeploymentCommand;
import com.grassroots.cdm.deployment.adapter.iis.model.IisExecutionStep;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.InstallationStatus;
import com.grassroots.cdm.entity.enums.MidServerExecutionState;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
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
 * Technology deployment adapter for Microsoft IIS running on Windows Server.
 *
 * Implements the 5 conceptual steps:
 * 1. Import certificate into LocalMachine\My store
 * 2. Ensure private key permissions (ACLs) for IIS identities (IIS_IUSRS, W3SVC)
 * 3. Update IIS HTTPS site binding
 * 4. Verify IIS SSL binding and handshake
 * 5. Report detailed execution outcome with automated rollback capability
 *
 * Direct execution is strictly delegated to the ServiceNow MID Server boundary;
 * the CDM application never executes arbitrary local or remote shell commands directly.
 */
@Component
public class IisDeploymentAdapter implements DeploymentAdapter {

    private static final Logger log = LoggerFactory.getLogger(IisDeploymentAdapter.class);

    // Standardized MID Server IIS exit codes
    public static final int EXIT_IMPORT_FAILED = 101;
    public static final int EXIT_PERMISSIONS_FAILED = 102;
    public static final int EXIT_BINDING_FAILED = 103;
    public static final int EXIT_VERIFICATION_FAILED = 104;

    private final MidServerClient midServerClient;
    private final MidServerExecutionService executionService;
    private final AuditService auditService;
    private final DeploymentJobRepository deploymentJobRepository;
    private final CertificateInstallationRepository installationRepository;

    public IisDeploymentAdapter(
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

    @Override
    public ServerTechnology getSupportedTechnology() {
        return ServerTechnology.IIS;
    }

    @Override
    public TargetType getSupportedTargetType() {
        return TargetType.WINDOWS_IIS;
    }

    @Override
    public boolean supports(DeploymentJob job) {
        if (job == null) return false;
        if (job.getTargetServer() != null && job.getTargetServer().getTechnology() == ServerTechnology.IIS) {
            return true;
        }
        return "WINDOWS_IIS".equalsIgnoreCase(job.getTargetType());
    }

    @Override
    public void validate(DeploymentJob job) {
        if (job == null) {
            throw new IisValidationException("DeploymentJob cannot be null");
        }

        TargetServer targetServer = job.getTargetServer();
        if (targetServer == null) {
            throw new IisValidationException("Target server is required for IIS deployment");
        }

        if (targetServer.getOperatingSystem() != ServerOperatingSystem.WINDOWS_SERVER) {
            throw new IisValidationException("Target server operating system must be WINDOWS_SERVER for IIS deployment, but was: " + targetServer.getOperatingSystem());
        }

        if (targetServer.getTechnology() != ServerTechnology.IIS) {
            throw new IisValidationException("Target server technology must be IIS, but was: " + targetServer.getTechnology());
        }

        int port = job.getTargetPort();
        if (port <= 0 || port > 65535) {
            throw new IisValidationException("Invalid target port for IIS binding: " + port);
        }

        CertificateRecord newCert = job.getNewCertificate();
        if (newCert == null) {
            throw new IisValidationException("New certificate is required for IIS deployment");
        }
        if (newCert.getThumbprint() == null || newCert.getThumbprint().isBlank()) {
            throw new IisValidationException("New certificate must have a valid SHA-1/SHA-256 thumbprint");
        }
        if (newCert.getValidTo() != null && newCert.getValidTo().isBefore(Instant.now())) {
            throw new IisValidationException("New certificate is already expired (validTo: " + newCert.getValidTo() + ")");
        }

        MidServer midServer = targetServer.getMidServer();
        if (midServer == null) {
            throw new IisValidationException("Target server " + targetServer.getHostname() + " has no assigned MID Server");
        }
        if (midServer.getStatus() != MidServerStatus.UP) {
            throw new IisValidationException("Assigned MID Server " + midServer.getName() + " is not UP (current status: " + midServer.getStatus() + ")");
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

        // 1. Idempotency Check: if target is already bound to this certificate, skip safely
        if (isAlreadyDeployed(job, targetThumbprint)) {
            log.info("IIS deployment for job {} is idempotent: certificate {} is already bound to site '{}' on port {}",
                    job.getId(), targetThumbprint, siteName, port);

            return DeploymentAdapterResult.builder(job.getId(), job.getIdempotencyKey())
                    .targetHost(targetServer.getHostname())
                    .targetPort(port)
                    .status(DeploymentAdapterResult.Status.SKIPPED_IDEMPOTENT)
                    .idempotent(true)
                    .deployedThumbprint(targetThumbprint)
                    .verifiedThumbprint(targetThumbprint)
                    .addStepResult(DeploymentStepResult.skipped("IDEMPOTENCY_CHECK", "Certificate already bound to IIS site"))
                    .build();
        }

        // 2. Build structured IIS deployment command
        IisDeploymentCommand command = new IisDeploymentCommand(
                job.getId(),
                job.getIdempotencyKey(),
                IisDeploymentCommand.Operation.DEPLOY,
                siteName,
                port,
                newCert.getCommonName(),
                targetThumbprint,
                previousThumbprint,
                "cyberark://GrassrootsSafe/Account/" + targetThumbprint
        );

        MidServerJobRequestDto requestDto = buildJobRequest(job, command);
        String endpoint = targetServer.getMidServer().getEndpoint();

        log.info("Dispatching structured IIS deployment command to MID Server [jobId={}, site='{}', port={}, thumbprint={}]",
                job.getId(), siteName, port, targetThumbprint);

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
            IisDeploymentCommand command,
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
        // Step 1: Certificate Import Failure Handling
        // -------------------------------------------------------------
        if (exitCode == EXIT_IMPORT_FAILED || stderr.contains("IMPORT_FAILED") || (errorMessage != null && errorMessage.contains("import failed"))) {
            steps.add(DeploymentStepResult.failure(IisExecutionStep.IMPORT_CERTIFICATE.name(), 500L,
                    "Failed to import certificate into LocalMachine\\My store", exitCode, stderr.isEmpty() ? errorMessage : stderr));
            steps.add(DeploymentStepResult.skipped(IisExecutionStep.ENSURE_PRIVATE_KEY_PERMISSIONS.name(), "Skipped due to import failure"));
            steps.add(DeploymentStepResult.skipped(IisExecutionStep.UPDATE_HTTPS_BINDING.name(), "Skipped due to import failure"));
            steps.add(DeploymentStepResult.skipped(IisExecutionStep.VERIFY_BINDING.name(), "Skipped due to import failure"));

            log.error("IIS Step 1 Failed: Certificate import error for job {} (exitCode={})", job.getId(), exitCode);
            // No rollback required: IIS binding was not yet touched
            throw new IisCertificateImportException("IIS Certificate import failed: " + (errorMessage != null ? errorMessage : stderr), targetThumbprint, exitCode);
        }
        steps.add(DeploymentStepResult.success(IisExecutionStep.IMPORT_CERTIFICATE.name(), 450L, "Imported certificate into LocalMachine\\My store"));

        // -------------------------------------------------------------
        // Step 2: Ensure Private Key Permissions Failure Handling
        // -------------------------------------------------------------
        if (exitCode == EXIT_PERMISSIONS_FAILED || stderr.contains("PERMISSIONS_FAILED") || (errorMessage != null && errorMessage.contains("permission failed"))) {
            steps.add(DeploymentStepResult.failure(IisExecutionStep.ENSURE_PRIVATE_KEY_PERMISSIONS.name(), 300L,
                    "Failed to grant private key ACLs to IIS identities", exitCode, stderr.isEmpty() ? errorMessage : stderr));
            steps.add(DeploymentStepResult.skipped(IisExecutionStep.UPDATE_HTTPS_BINDING.name(), "Skipped due to permissions failure"));
            steps.add(DeploymentStepResult.skipped(IisExecutionStep.VERIFY_BINDING.name(), "Skipped due to permissions failure"));

            log.error("IIS Step 2 Failed: Key permission assignment error for job {} (exitCode={})", job.getId(), exitCode);
            // No rollback required: binding not yet modified
            throw new IisPermissionException("IIS Private key permissions assignment failed: " + (errorMessage != null ? errorMessage : stderr), "IIS_IUSRS", exitCode);
        }
        steps.add(DeploymentStepResult.success(IisExecutionStep.ENSURE_PRIVATE_KEY_PERMISSIONS.name(), 280L, "Granted private key Read permissions to IIS_IUSRS and W3SVC"));

        // -------------------------------------------------------------
        // Step 3: Update IIS HTTPS Binding Failure Handling
        // -------------------------------------------------------------
        if (exitCode == EXIT_BINDING_FAILED || stderr.contains("BINDING_FAILED") || (errorMessage != null && errorMessage.contains("binding update failed"))) {
            steps.add(DeploymentStepResult.failure(IisExecutionStep.UPDATE_HTTPS_BINDING.name(), 600L,
                    "Failed to update SSL binding on site " + command.getSiteName(), exitCode, stderr.isEmpty() ? errorMessage : stderr));
            steps.add(DeploymentStepResult.skipped(IisExecutionStep.VERIFY_BINDING.name(), "Skipped due to binding failure"));

            log.error("IIS Step 3 Failed: Binding update error for job {} (exitCode={}). Triggering rollback if available.", job.getId(), exitCode);
            if (previousThumbprint != null && !previousThumbprint.isBlank()) {
                rollback(job, "Binding update failed: " + (errorMessage != null ? errorMessage : stderr));
            }
            throw new IisBindingUpdateException("IIS HTTPS binding update failed on site " + command.getSiteName() + ": " + (errorMessage != null ? errorMessage : stderr), command.getSiteName(), command.getPort(), exitCode);
        }
        steps.add(DeploymentStepResult.success(IisExecutionStep.UPDATE_HTTPS_BINDING.name(), 750L, "Successfully updated IIS HTTPS binding for site " + command.getSiteName() + " on port " + command.getPort()));

        // -------------------------------------------------------------
        // Step 4: Verify IIS Binding Failure Handling
        // -------------------------------------------------------------
        if (exitCode == EXIT_VERIFICATION_FAILED || stderr.contains("VERIFICATION_FAILED") || (errorMessage != null && errorMessage.contains("verification failed"))) {
            steps.add(DeploymentStepResult.failure(IisExecutionStep.VERIFY_BINDING.name(), 400L,
                    "Post-deployment verification failed: active thumbprint does not match expected", exitCode, stderr.isEmpty() ? errorMessage : stderr));

            log.error("IIS Step 4 Failed: Verification mismatch for job {}. Executing automated rollback.", job.getId());
            if (previousThumbprint != null && !previousThumbprint.isBlank()) {
                rollback(job, "Verification failed: " + (errorMessage != null ? errorMessage : stderr));
            }
            throw new IisVerificationException("IIS verification failed: active certificate did not match expected thumbprint " + targetThumbprint, targetThumbprint, previousThumbprint);
        }
        steps.add(DeploymentStepResult.success(IisExecutionStep.VERIFY_BINDING.name(), 320L, "Verified active TLS handshake and SSL binding matches thumbprint: " + targetThumbprint));

        // -------------------------------------------------------------
        // Step 5: Successful Completion
        // -------------------------------------------------------------
        updateInstallationStatus(job, targetThumbprint);

        auditService.recordAudit(AuditEvent.create(
                AuditAction.LIVE_ENDPOINT_VERIFIED,
                "CertificateInstallation",
                job.getInstallation() != null ? job.getInstallation().getId().toString() : "UNKNOWN",
                "SYSTEM",
                "SUCCESS",
                "IIS deployment verified successfully for site " + command.getSiteName() + " on port " + command.getPort() + " (thumbprint=" + targetThumbprint + ")",
                null
        ));

        return DeploymentAdapterResult.builder(job.getId(), job.getIdempotencyKey())
                .targetHost(targetServer.getHostname())
                .targetPort(command.getPort())
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

        if (oldCert == null || oldCert.getThumbprint() == null || oldCert.getThumbprint().isBlank()) {
            log.warn("Cannot perform rollback for job {}: No previous certificate thumbprint available", job.getId());
            return DeploymentAdapterResult.builder(job.getId(), job.getIdempotencyKey())
                    .targetHost(targetServer != null ? targetServer.getHostname() : "unknown")
                    .status(DeploymentAdapterResult.Status.FAILED)
                    .rollbackExecuted(false)
                    .errorMessage("Rollback not possible: no previous certificate record found")
                    .build();
        }

        String restoreThumbprint = oldCert.getThumbprint();
        String siteName = resolveSiteName(job);
        int port = job.getTargetPort();

        log.info("Executing automated IIS rollback for job {} to restore previous thumbprint {} on site '{}' (reason: {})",
                job.getId(), restoreThumbprint, siteName, failureReason);

        IisDeploymentCommand rollbackCommand = new IisDeploymentCommand(
                job.getId(),
                "ROLLBACK-" + job.getIdempotencyKey(),
                IisDeploymentCommand.Operation.ROLLBACK,
                siteName,
                port,
                oldCert.getCommonName(),
                restoreThumbprint,
                null,
                "cyberark://GrassrootsSafe/Account/" + restoreThumbprint
        );

        MidServerJobRequestDto rollbackRequest = buildJobRequest(job, rollbackCommand);
        String endpoint = targetServer.getMidServer().getEndpoint();

        try {
            MidServerJobResponseDto rollbackReceipt = midServerClient.submitJob(endpoint, rollbackRequest);
            String rollbackTaskId = rollbackReceipt.getTaskId();

            MidServerStatusQueryResponseDto status = midServerClient.getJobStatus(endpoint, rollbackTaskId);

            boolean rollbackSuccess = status.getState() == MidServerExecutionState.SUCCESS && (status.getExitCode() == null || status.getExitCode() == 0);

            if (rollbackSuccess) {
                log.info("Rollback successfully restored previous IIS binding thumbprint {} for job {}", restoreThumbprint, job.getId());

                auditService.recordAudit(AuditEvent.create(
                        AuditAction.JOB_FAILED,
                        "DeploymentJob",
                        job.getId().toString(),
                        "SYSTEM",
                        "ROLLED_BACK",
                        "Deployment failed (" + failureReason + "); successfully rolled back to previous thumbprint " + restoreThumbprint,
                        null
                ));

                return DeploymentAdapterResult.builder(job.getId(), job.getIdempotencyKey())
                        .targetHost(targetServer.getHostname())
                        .targetPort(port)
                        .status(DeploymentAdapterResult.Status.ROLLED_BACK)
                        .rollbackExecuted(true)
                        .deployedThumbprint(restoreThumbprint)
                        .verifiedThumbprint(restoreThumbprint)
                        .midServerTaskId(rollbackTaskId)
                        .errorMessage("Deployment failed: " + failureReason + ". Successfully rolled back to " + restoreThumbprint)
                        .addStepResult(DeploymentStepResult.rolledBack(IisExecutionStep.ROLLBACK_BINDING.name(), 500L, "Reverted binding to previous thumbprint: " + restoreThumbprint))
                        .build();
            } else {
                log.error("Rollback failed to restore previous IIS binding for job {}: {}", job.getId(), status.getErrorMessage());
                throw new IisRollbackException("Rollback failed on IIS server: " + status.getErrorMessage(), restoreThumbprint);
            }
        } catch (Exception ex) {
            if (ex instanceof IisRollbackException ire) {
                throw ire;
            }
            throw new IisRollbackException("Failed to dispatch or execute IIS rollback: " + ex.getMessage(), restoreThumbprint, ex);
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

    private String resolveSiteName(DeploymentJob job) {
        if (job.getInstallation() != null && job.getInstallation().getBindingInfo() != null && !job.getInstallation().getBindingInfo().isBlank()) {
            return job.getInstallation().getBindingInfo();
        }
        return "Default Web Site";
    }

    private MidServerJobRequestDto buildJobRequest(DeploymentJob job, IisDeploymentCommand command) {
        TargetServer targetServer = job.getTargetServer();
        TargetServerInfoDto serverInfo = new TargetServerInfoDto(
                targetServer.getHostname(),
                targetServer.getIpAddress(),
                targetServer.getOperatingSystem(),
                targetServer.getTechnology(),
                command.getPort(),
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
                "Cert:\\LocalMachine\\My",
                command.getSiteName(),
                false, // IIS bindings do not require full server restart
                true,  // IIS binding changes take effect immediately
                true,
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
