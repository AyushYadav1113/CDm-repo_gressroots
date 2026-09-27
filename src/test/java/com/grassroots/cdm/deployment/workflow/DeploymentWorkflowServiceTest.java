package com.grassroots.cdm.deployment.workflow;

import com.grassroots.cdm.deployment.DeploymentDispatcher;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.deployment.workflow.exception.DuplicateProcessingException;
import com.grassroots.cdm.deployment.workflow.exception.MaxRetriesExceededException;
import com.grassroots.cdm.deployment.workflow.impl.DefaultDeploymentWorkflowService;
import com.grassroots.cdm.deployment.workflow.statemachine.DeploymentStateMachine;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.DeploymentType;
import com.grassroots.cdm.entity.enums.InstallationStatus;
import com.grassroots.cdm.integration.CredentialSecret;
import com.grassroots.cdm.integration.CyberArkVaultClient;
import com.grassroots.cdm.repository.CertificateInstallationRepository;
import com.grassroots.cdm.repository.CertificateRecordRepository;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import com.grassroots.cdm.verification.EndpointVerifier;
import com.grassroots.cdm.verification.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeploymentWorkflowService Unit Tests")
class DeploymentWorkflowServiceTest {

    @Mock
    private DeploymentJobRepository deploymentJobRepository;

    @Mock
    private DeploymentStateMachine stateMachine;

    @Mock
    private CertificateInstallationRepository installationRepository;

    @Mock
    private CertificateRecordRepository certificateRecordRepository;

    @Mock
    private CyberArkVaultClient cyberArkVaultClient;

    @Mock
    private DeploymentDispatcher deploymentDispatcher;

    @Mock
    private EndpointVerifier endpointVerifier;

    private DefaultDeploymentWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        workflowService = new DefaultDeploymentWorkflowService(
                deploymentJobRepository,
                stateMachine,
                installationRepository,
                certificateRecordRepository
        );
        workflowService.setCyberArkVaultClient(cyberArkVaultClient);
        workflowService.setDeploymentDispatcher(deploymentDispatcher);
        workflowService.setEndpointVerifier(endpointVerifier);
    }

    private DeploymentJob createJob(UUID id, DeploymentJobStatus status) {
        DeploymentJob job = new DeploymentJob();
        job.setId(id);
        job.setJobReference("JOB-WF-001");
        job.setIdempotencyKey("DEP:wf:443:001");
        job.setStatus(status);
        job.setTargetHost("app.grassroots.internal");
        job.setTargetPort(443);
        job.setTargetType("IIS");
        job.setDeploymentType(DeploymentType.IIS);
        job.setAttemptCount(0);
        job.setMaxRetries(3);

        CertificateRecord oldCert = new CertificateRecord();
        oldCert.setId(UUID.randomUUID());
        job.setOldCertificate(oldCert);

        CertificateRecord newCert = new CertificateRecord();
        newCert.setId(UUID.randomUUID());
        newCert.setFingerprintSha256("NEW-THUMBPRINT-SHA256");
        job.setNewCertificate(newCert);

        CertificateInstallation install = new CertificateInstallation();
        install.setId(UUID.randomUUID());
        install.setStatus(InstallationStatus.INSTALLED);
        job.setInstallation(install);

        return job;
    }

    // =========================================================================
    // 1. STEP-BY-STEP WORKFLOW ADVANCEMENT
    // =========================================================================
    @Test
    @DisplayName("advanceJob sequentially advances job through credential acquisition, dispatch, running, deployment, and verification")
    void testSequentialJobAdvancement() {
        UUID jobId = UUID.randomUUID();
        DeploymentJob job = createJob(jobId, DeploymentJobStatus.PLANNED);

        when(deploymentJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(stateMachine.transition(any(DeploymentJob.class), any(DeploymentJobStatus.class), anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    DeploymentJob j = inv.getArgument(0);
                    DeploymentJobStatus target = inv.getArgument(1);
                    j.setStatus(target);
                    return j;
                });

        CredentialSecret secret = new CredentialSecret("cdm_svc", "secret123".toCharArray(), null, "app.grassroots.internal");
        when(cyberArkVaultClient.retrieveCredential(anyString(), anyString(), anyString())).thenReturn(secret);
        when(deploymentDispatcher.dispatchJob(any(DeploymentJob.class))).thenReturn("MID-TASK-8888");
        when(endpointVerifier.verifyEndpoint(anyString(), eq(443), anyString()))
                .thenReturn(VerificationResult.successful("app.grassroots.internal", 443, "NEW-THUMBPRINT-SHA256", "Handshake success"));

        // 1. Advance from PLANNED -> acquires credentials -> CREDENTIALS_ACQUIRED
        DeploymentJob afterCreds = workflowService.advanceJob(jobId, "CORR-01");
        assertThat(afterCreds.getStatus()).isEqualTo(DeploymentJobStatus.CREDENTIALS_ACQUIRED);
        verify(cyberArkVaultClient).retrieveCredential(anyString(), anyString(), anyString());

        // 2. Advance from CREDENTIALS_ACQUIRED -> dispatches to MID -> SENT_TO_MID
        DeploymentJob afterDispatch = workflowService.advanceJob(jobId, "CORR-01");
        assertThat(afterDispatch.getStatus()).isEqualTo(DeploymentJobStatus.SENT_TO_MID);
        assertThat(afterDispatch.getMidServerTaskId()).isEqualTo("MID-TASK-8888");

        // 3. Advance from SENT_TO_MID -> acknowledges running -> RUNNING
        DeploymentJob afterRunning = workflowService.advanceJob(jobId, "CORR-01");
        assertThat(afterRunning.getStatus()).isEqualTo(DeploymentJobStatus.RUNNING);

        // 4. Advance from RUNNING -> mark deployed -> DEPLOYED
        DeploymentJob afterDeployed = workflowService.advanceJob(jobId, "CORR-01");
        assertThat(afterDeployed.getStatus()).isEqualTo(DeploymentJobStatus.DEPLOYED);

        // 5. Advance from DEPLOYED -> verifies live endpoint -> VERIFIED
        DeploymentJob afterVerified = workflowService.advanceJob(jobId, "CORR-01");
        assertThat(afterVerified.getStatus()).isEqualTo(DeploymentJobStatus.VERIFIED);
        verify(endpointVerifier).verifyEndpoint(anyString(), eq(443), anyString());

        // 6. Advance from VERIFIED -> completes job -> COMPLETED
        DeploymentJob afterCompleted = workflowService.advanceJob(jobId, "CORR-01");
        assertThat(afterCompleted.getStatus()).isEqualTo(DeploymentJobStatus.COMPLETED);
        verify(installationRepository).saveAndFlush(any());
        verify(certificateRecordRepository).saveAndFlush(any());
    }

    // =========================================================================
    // 2. COMPLETED JOB REJECTION
    // =========================================================================
    @Test
    @DisplayName("Advancing an already COMPLETED job throws DuplicateProcessingException")
    void testCompletedJobThrowsException() {
        UUID jobId = UUID.randomUUID();
        DeploymentJob completedJob = createJob(jobId, DeploymentJobStatus.COMPLETED);

        when(deploymentJobRepository.findById(jobId)).thenReturn(Optional.of(completedJob));

        DuplicateProcessingException ex = assertThrows(DuplicateProcessingException.class, () ->
                workflowService.advanceJob(jobId, "CORR-DONE")
        );
        assertThat(ex.getJobId()).isEqualTo(jobId);
        assertThat(ex.getMessage()).contains("COMPLETED");
    }

    // =========================================================================
    // 3. FAILURE AND RETRY HANDLING
    // =========================================================================
    @Test
    @DisplayName("Failure during credential acquisition with remaining retries schedules RETRY_PENDING")
    void testFailureSchedulesRetryWhenAttemptsRemain() {
        UUID jobId = UUID.randomUUID();
        DeploymentJob job = createJob(jobId, DeploymentJobStatus.CREDENTIALS_PENDING);
        job.setAttemptCount(0);
        job.setMaxRetries(3);

        when(deploymentJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(stateMachine.transition(any(DeploymentJob.class), any(DeploymentJobStatus.class), anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    DeploymentJob j = inv.getArgument(0);
                    DeploymentJobStatus target = inv.getArgument(1);
                    j.setStatus(target);
                    return j;
                });

        DeploymentJob result = workflowService.handleFailure(jobId, "Simulated CyberArk connection timeout", true, "CORR-FAIL");

        assertThat(result.getStatus()).isEqualTo(DeploymentJobStatus.RETRY_PENDING);
        assertThat(result.getAttemptCount()).isEqualTo(1);
        assertThat(result.getErrorMessage()).contains("CyberArk connection timeout");
    }

    // =========================================================================
    // 4. RETRY EXHAUSTION ESCALATES TO MANUAL REVIEW
    // =========================================================================
    @Test
    @DisplayName("Failure when max retries are exhausted escalates to MANUAL_REVIEW")
    void testMaxRetriesExhaustionEscalatesToManualReview() {
        UUID jobId = UUID.randomUUID();
        DeploymentJob job = createJob(jobId, DeploymentJobStatus.CREDENTIALS_PENDING);
        job.setAttemptCount(3);
        job.setMaxRetries(3); // Already at max!

        when(deploymentJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(stateMachine.transition(any(DeploymentJob.class), any(DeploymentJobStatus.class), anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    DeploymentJob j = inv.getArgument(0);
                    DeploymentJobStatus target = inv.getArgument(1);
                    j.setStatus(target);
                    return j;
                });

        DeploymentJob result = workflowService.handleFailure(jobId, "Persistent endpoint timeout", true, "CORR-EXHAUST");

        assertThat(result.getStatus()).isEqualTo(DeploymentJobStatus.MANUAL_REVIEW);
        assertThat(result.getErrorMessage()).contains("Persistent endpoint timeout");
    }

    // =========================================================================
    // 5. MANUAL REVIEW APPROVAL
    // =========================================================================
    @Test
    @DisplayName("Admin approval from MANUAL_REVIEW transitions to RETRY_PENDING")
    void testApproveFromManualReview() {
        UUID jobId = UUID.randomUUID();
        DeploymentJob job = createJob(jobId, DeploymentJobStatus.MANUAL_REVIEW);

        when(deploymentJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(stateMachine.transition(any(DeploymentJob.class), any(DeploymentJobStatus.class), anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    DeploymentJob j = inv.getArgument(0);
                    DeploymentJobStatus target = inv.getArgument(1);
                    j.setStatus(target);
                    return j;
                });

        DeploymentJob approved = workflowService.approveFromManualReview(jobId, "Admin unblocked credentials", "secops-admin", "CORR-APPROVE");

        assertThat(approved.getStatus()).isEqualTo(DeploymentJobStatus.RETRY_PENDING);
    }

    // =========================================================================
    // 6. CONCURRENT PROCESSING PROTECTION
    // =========================================================================
    @Test
    @DisplayName("Concurrent attempts to advance the same job are blocked by in-flight execution lock")
    void testConcurrentExecutionProtection() throws Exception {
        UUID jobId = UUID.randomUUID();
        DeploymentJob job = createJob(jobId, DeploymentJobStatus.PLANNED);

        when(deploymentJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(stateMachine.transition(any(DeploymentJob.class), any(DeploymentJobStatus.class), anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    // Introduce a deliberate delay in the transition to simulate active processing
                    Thread.sleep(150);
                    DeploymentJob j = inv.getArgument(0);
                    j.setStatus(inv.getArgument(1));
                    return j;
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                startLatch.await();
                workflowService.advanceJob(jobId, "CORR-CONCURRENT");
                successCount.incrementAndGet();
            } catch (DuplicateProcessingException e) {
                duplicateCount.incrementAndGet();
            } catch (Exception ignored) {}
        };

        Future<?> f1 = executor.submit(task);
        Future<?> f2 = executor.submit(task);

        startLatch.countDown(); // Launch both threads simultaneously

        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 1 thread must succeed, and the 2nd thread must be rejected with DuplicateProcessingException
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(1);
    }
}
