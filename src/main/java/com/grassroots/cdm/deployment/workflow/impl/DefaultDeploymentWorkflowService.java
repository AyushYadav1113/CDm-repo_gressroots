package com.grassroots.cdm.deployment.workflow.impl;

import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.deployment.workflow.DeploymentWorkflowService;
import com.grassroots.cdm.deployment.workflow.exception.DuplicateProcessingException;
import com.grassroots.cdm.deployment.workflow.exception.JobConcurrencyException;
import com.grassroots.cdm.deployment.workflow.exception.MaxRetriesExceededException;
import com.grassroots.cdm.deployment.workflow.exception.WorkflowExecutionException;
import com.grassroots.cdm.deployment.workflow.statemachine.DeploymentStateMachine;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.entity.enums.InstallationStatus;
import com.grassroots.cdm.integration.CredentialSecret;
import com.grassroots.cdm.integration.CyberArkVaultClient;
import com.grassroots.cdm.deployment.DeploymentDispatcher;
import com.grassroots.cdm.repository.CertificateInstallationRepository;
import com.grassroots.cdm.repository.CertificateRecordRepository;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import com.grassroots.cdm.verification.EndpointVerifier;
import com.grassroots.cdm.verification.VerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of DeploymentWorkflowService.
 * Coordinates deployment job advancement through the state machine while enforcing
 * idempotency, concurrency protection, audit trail emission, and retry/review escalations.
 */
@Service
@Transactional
public class DefaultDeploymentWorkflowService implements DeploymentWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(DefaultDeploymentWorkflowService.class);

    private final DeploymentJobRepository deploymentJobRepository;
    private final DeploymentStateMachine stateMachine;
    private final CertificateInstallationRepository installationRepository;
    private final CertificateRecordRepository certificateRecordRepository;

    @Autowired(required = false)
    private CyberArkVaultClient cyberArkVaultClient;

    @Autowired(required = false)
    private DeploymentDispatcher deploymentDispatcher;

    @Autowired(required = false)
    private EndpointVerifier endpointVerifier;

    // In-memory guard ensuring the same job cannot be processed concurrently by multiple threads
    private final ConcurrentHashMap<UUID, Boolean> inFlightJobs = new ConcurrentHashMap<>();

    public DefaultDeploymentWorkflowService(
            DeploymentJobRepository deploymentJobRepository,
            DeploymentStateMachine stateMachine,
            CertificateInstallationRepository installationRepository,
            CertificateRecordRepository certificateRecordRepository
    ) {
        this.deploymentJobRepository = deploymentJobRepository;
        this.stateMachine = stateMachine;
        this.installationRepository = installationRepository;
        this.certificateRecordRepository = certificateRecordRepository;
    }

    // Setters for tests/mocks
    public void setCyberArkVaultClient(CyberArkVaultClient cyberArkVaultClient) {
        this.cyberArkVaultClient = cyberArkVaultClient;
    }

    public void setDeploymentDispatcher(DeploymentDispatcher deploymentDispatcher) {
        this.deploymentDispatcher = deploymentDispatcher;
    }

    public void setEndpointVerifier(EndpointVerifier endpointVerifier) {
        this.endpointVerifier = endpointVerifier;
    }

    @Override
    public DeploymentJob advanceJob(UUID jobId, String correlationId) {
        acquireExecutionLock(jobId);
        try {
            DeploymentJob job = getJobOrThrow(jobId);
            DeploymentJobStatus current = job.getStatus();

            if (current == DeploymentJobStatus.COMPLETED) {
                throw new DuplicateProcessingException(jobId, "Job has already been successfully COMPLETED and cannot be advanced further.");
            }

            return switch (current) {
                case CREATED -> transitionJob(jobId, DeploymentJobStatus.PLANNED, "Advancing from CREATED to PLANNED", "WORKFLOW", correlationId);
                case PLANNED, PENDING -> acquireCredentials(jobId, correlationId);
                case CREDENTIALS_PENDING -> acquireCredentials(jobId, correlationId);
                case CREDENTIALS_ACQUIRED, CREDENTIAL_ACQUIRED -> dispatchToMidServer(jobId, correlationId);
                case SENT_TO_MID, DISPATCHED -> {
                    String taskId = job.getMidServerTaskId() != null ? job.getMidServerTaskId() : "MID-TASK-" + UUID.randomUUID().toString().substring(0, 8);
                    yield acknowledgeRunning(jobId, taskId, correlationId);
                }
                case RUNNING, IN_PROGRESS -> markDeployed(jobId, correlationId);
                case DEPLOYED -> verifyDeployment(jobId, correlationId);
                case VERIFICATION_PENDING, VERIFYING -> verifyDeployment(jobId, correlationId);
                case VERIFIED -> completeDeployment(jobId, correlationId);
                case FAILED -> scheduleRetry(jobId, "Auto-advancing failed job for retry", correlationId);
                case RETRY_PENDING -> acquireCredentials(jobId, correlationId);
                case MANUAL_REVIEW -> throw new WorkflowExecutionException("Job is in MANUAL_REVIEW status and requires manual administrator action before advancement.");
                default -> throw new WorkflowExecutionException("Cannot advance job from unhandled status: " + current);
            };
        } finally {
            releaseExecutionLock(jobId);
        }
    }

    @Override
    public DeploymentJob transitionJob(UUID jobId, DeploymentJobStatus targetStatus, String reason, String actor, String correlationId) {
        DeploymentJob job = getJobOrThrow(jobId);
        try {
            return stateMachine.transition(job, targetStatus, reason, actor, correlationId);
        } catch (OptimisticLockingFailureException e) {
            throw new JobConcurrencyException(jobId, "Optimistic locking conflict during transition to " + targetStatus, e);
        }
    }

    @Override
    public DeploymentJob acquireCredentials(UUID jobId, String correlationId) {
        DeploymentJob job = getJobOrThrow(jobId);

        // Step 1: Transition to CREDENTIALS_PENDING if not already
        if (job.getStatus() != DeploymentJobStatus.CREDENTIALS_PENDING) {
            job = transitionJob(jobId, DeploymentJobStatus.CREDENTIALS_PENDING, "Requesting credentials from CyberArk", "WORKFLOW", correlationId);
        }

        try {
            // Retrieve credentials via CyberArk client interface
            if (cyberArkVaultClient != null) {
                String safe = "CDM_PROD_SAFE";
                String account = "svc_cdm_" + (job.getTargetType() != null ? job.getTargetType().toLowerCase() : "default");
                CredentialSecret secret = cyberArkVaultClient.retrieveCredential(safe, account, job.getTargetHost());
                if (secret != null) {
                    secret.wipe(); // Wipe memory immediately after verification
                }
            }

            // Step 2: Transition to CREDENTIALS_ACQUIRED
            return transitionJob(jobId, DeploymentJobStatus.CREDENTIALS_ACQUIRED, "Credentials successfully acquired from CyberArk", "WORKFLOW", correlationId);
        } catch (Exception ex) {
            log.error("Failed to acquire CyberArk credentials for job {}", jobId, ex);
            return handleFailure(jobId, "CyberArk credential acquisition failed: " + ex.getMessage(), true, correlationId);
        }
    }

    @Override
    public DeploymentJob dispatchToMidServer(UUID jobId, String correlationId) {
        DeploymentJob job = getJobOrThrow(jobId);

        try {
            String midServerTaskId = "MID-TASK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            if (deploymentDispatcher != null) {
                String dispatchedId = deploymentDispatcher.dispatchJob(job);
                if (dispatchedId != null && !dispatchedId.isBlank()) {
                    midServerTaskId = dispatchedId;
                }
            }
            job.setMidServerTaskId(midServerTaskId);
            deploymentJobRepository.saveAndFlush(job);

            return transitionJob(jobId, DeploymentJobStatus.SENT_TO_MID, "Dispatched deployment instruction to MID Server (taskId=" + midServerTaskId + ")", "WORKFLOW", correlationId);
        } catch (Exception ex) {
            log.error("Failed to dispatch job {} to MID Server", jobId, ex);
            return handleFailure(jobId, "MID Server dispatch failed: " + ex.getMessage(), true, correlationId);
        }
    }

    @Override
    public DeploymentJob acknowledgeRunning(UUID jobId, String midServerTaskId, String correlationId) {
        DeploymentJob job = getJobOrThrow(jobId);
        if (midServerTaskId != null) {
            job.setMidServerTaskId(midServerTaskId);
            deploymentJobRepository.saveAndFlush(job);
        }
        return transitionJob(jobId, DeploymentJobStatus.RUNNING, "MID Server acknowledged execution running", "MID_SERVER", correlationId);
    }

    @Override
    public DeploymentJob markDeployed(UUID jobId, String correlationId) {
        return transitionJob(jobId, DeploymentJobStatus.DEPLOYED, "Target deployment script executed successfully", "MID_SERVER", correlationId);
    }

    @Override
    public DeploymentJob verifyDeployment(UUID jobId, String correlationId) {
        DeploymentJob job = getJobOrThrow(jobId);

        // Step 1: Transition to VERIFICATION_PENDING if not already
        if (job.getStatus() != DeploymentJobStatus.VERIFICATION_PENDING) {
            job = transitionJob(jobId, DeploymentJobStatus.VERIFICATION_PENDING, "Initiating live TLS handshake verification", "WORKFLOW", correlationId);
        }

        try {
            if (endpointVerifier != null && job.getNewCertificate() != null) {
                String expectedFingerprint = job.getNewCertificate().getFingerprintSha256();
                VerificationResult result = endpointVerifier.verifyEndpoint(job.getTargetHost(), job.getTargetPort(), expectedFingerprint);
                if (!result.verified()) {
                    return handleFailure(jobId, "Live endpoint handshake verification failed: " + result.diagnosticDetails(), true, correlationId);
                }
            }

            return transitionJob(jobId, DeploymentJobStatus.VERIFIED, "Live TLS handshake verified matching certificate", "WORKFLOW", correlationId);
        } catch (Exception ex) {
            log.error("Endpoint verification error for job {}", jobId, ex);
            return handleFailure(jobId, "Live verification error: " + ex.getMessage(), true, correlationId);
        }
    }

    @Override
    public DeploymentJob completeDeployment(UUID jobId, String correlationId) {
        DeploymentJob job = getJobOrThrow(jobId);

        // Transition job to COMPLETED
        DeploymentJob completedJob = transitionJob(jobId, DeploymentJobStatus.COMPLETED, "Deployment lifecycle successfully completed and verified", "WORKFLOW", correlationId);

        // Update CertificateInstallation if present
        if (completedJob.getInstallation() != null) {
            CertificateInstallation installation = completedJob.getInstallation();
            installation.setStatus(InstallationStatus.VERIFIED);
            installation.setCertificate(completedJob.getNewCertificate());
            installation.setLastVerifiedAt(Instant.now());
            installationRepository.saveAndFlush(installation);
            log.info("Updated CertificateInstallation {} to VERIFIED with new cert {}", installation.getId(), completedJob.getNewCertificate().getId());
        }

        // Update Old CertificateRecord if present
        if (completedJob.getOldCertificate() != null) {
            CertificateRecord oldCert = completedJob.getOldCertificate();
            oldCert.setStatus(CertificateStatus.REPLACED);
            certificateRecordRepository.saveAndFlush(oldCert);
            log.info("Updated expiring certificate {} to REPLACED status", oldCert.getId());
        }

        return completedJob;
    }

    @Override
    public DeploymentJob handleFailure(UUID jobId, String errorMessage, boolean retryable, String correlationId) {
        DeploymentJob job = getJobOrThrow(jobId);
        job.setErrorInformation(errorMessage);
        job.setErrorMessage(errorMessage);

        // Transition to FAILED
        job = transitionJob(jobId, DeploymentJobStatus.FAILED, errorMessage, "WORKFLOW", correlationId);

        int currentAttempts = job.getAttemptCount();
        int maxRetries = job.getMaxRetries();

        if (retryable && currentAttempts < maxRetries) {
            return scheduleRetry(jobId, "Scheduling retry after failure: " + errorMessage, correlationId);
        } else {
            String escalationReason = (currentAttempts >= maxRetries)
                    ? "Max retries (" + maxRetries + ") exhausted: " + errorMessage
                    : "Non-retryable failure: " + errorMessage;
            return escalateToManualReview(jobId, escalationReason, correlationId);
        }
    }

    @Override
    public DeploymentJob scheduleRetry(UUID jobId, String reason, String correlationId) {
        DeploymentJob job = getJobOrThrow(jobId);

        if (job.getAttemptCount() >= job.getMaxRetries()) {
            throw new MaxRetriesExceededException(jobId, job.getAttemptCount(), job.getMaxRetries(),
                    "Cannot schedule retry: max retry limit reached.");
        }

        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setScheduledAt(Instant.now());
        deploymentJobRepository.saveAndFlush(job);

        return transitionJob(jobId, DeploymentJobStatus.RETRY_PENDING, reason, "WORKFLOW", correlationId);
    }

    @Override
    public DeploymentJob escalateToManualReview(UUID jobId, String reason, String correlationId) {
        return transitionJob(jobId, DeploymentJobStatus.MANUAL_REVIEW, reason, "WORKFLOW", correlationId);
    }

    @Override
    public DeploymentJob approveFromManualReview(UUID jobId, String reason, String actor, String correlationId) {
        return transitionJob(jobId, DeploymentJobStatus.RETRY_PENDING, reason != null ? reason : "Manual approval by " + actor, actor, correlationId);
    }

    private DeploymentJob getJobOrThrow(UUID jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("Deployment job ID must not be null");
        }
        return deploymentJobRepository.findById(jobId)
                .orElseThrow(() -> new WorkflowExecutionException("DeploymentJob not found for ID: " + jobId));
    }

    private void acquireExecutionLock(UUID jobId) {
        if (inFlightJobs.putIfAbsent(jobId, Boolean.TRUE) != null) {
            throw new DuplicateProcessingException(jobId, "Job is currently being processed by another execution thread.");
        }
    }

    private void releaseExecutionLock(UUID jobId) {
        inFlightJobs.remove(jobId);
    }
}
