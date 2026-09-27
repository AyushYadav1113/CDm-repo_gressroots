package com.grassroots.cdm.deployment.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.deployment.workflow.exception.InvalidStateTransitionException;
import com.grassroots.cdm.deployment.workflow.statemachine.DefaultDeploymentStateMachine;
import com.grassroots.cdm.deployment.workflow.statemachine.DeploymentStateMachine;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeploymentStateMachine Unit Tests")
class DeploymentStateMachineTest {

    @Mock
    private DeploymentJobRepository deploymentJobRepository;

    @Mock
    private AuditService auditService;

    private DeploymentStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new DefaultDeploymentStateMachine(
                deploymentJobRepository,
                auditService,
                new ObjectMapper()
        );
    }

    private DeploymentJob createJob(DeploymentJobStatus status) {
        DeploymentJob job = new DeploymentJob();
        job.setId(UUID.randomUUID());
        job.setJobReference("JOB-TEST-001");
        job.setIdempotencyKey("DEP:test:443:001");
        job.setStatus(status);
        return job;
    }

    // =========================================================================
    // 1. VALID TRANSITIONS: HAPPY PATH
    // =========================================================================
    @Test
    @DisplayName("Valid transition path: CREATED -> PLANNED -> CREDENTIALS_PENDING -> CREDENTIALS_ACQUIRED -> SENT_TO_MID -> RUNNING -> DEPLOYED -> VERIFICATION_PENDING -> VERIFIED -> COMPLETED")
    void testCompleteHappyPathTransitions() {
        DeploymentJob job = createJob(DeploymentJobStatus.CREATED);
        when(deploymentJobRepository.saveAndFlush(any(DeploymentJob.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. CREATED -> PLANNED
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.CREATED, DeploymentJobStatus.PLANNED));
        job = stateMachine.transition(job, DeploymentJobStatus.PLANNED, "Planned by planner", "TEST");
        assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.PLANNED);

        // 2. PLANNED -> CREDENTIALS_PENDING
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.PLANNED, DeploymentJobStatus.CREDENTIALS_PENDING));
        job = stateMachine.transition(job, DeploymentJobStatus.CREDENTIALS_PENDING, "Acquiring creds", "TEST");
        assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.CREDENTIALS_PENDING);

        // 3. CREDENTIALS_PENDING -> CREDENTIALS_ACQUIRED
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.CREDENTIALS_PENDING, DeploymentJobStatus.CREDENTIALS_ACQUIRED));
        job = stateMachine.transition(job, DeploymentJobStatus.CREDENTIALS_ACQUIRED, "Creds acquired", "TEST");
        assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.CREDENTIALS_ACQUIRED);

        // 4. CREDENTIALS_ACQUIRED -> SENT_TO_MID
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.CREDENTIALS_ACQUIRED, DeploymentJobStatus.SENT_TO_MID));
        job = stateMachine.transition(job, DeploymentJobStatus.SENT_TO_MID, "Dispatched to MID", "TEST");
        assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.SENT_TO_MID);
        assertThat(job.getDispatchedAt()).isNotNull();

        // 5. SENT_TO_MID -> RUNNING
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.SENT_TO_MID, DeploymentJobStatus.RUNNING));
        job = stateMachine.transition(job, DeploymentJobStatus.RUNNING, "MID acknowledged running", "TEST");
        assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.RUNNING);
        assertThat(job.getStartedAt()).isNotNull();

        // 6. RUNNING -> DEPLOYED
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.RUNNING, DeploymentJobStatus.DEPLOYED));
        job = stateMachine.transition(job, DeploymentJobStatus.DEPLOYED, "Target scripts done", "TEST");
        assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.DEPLOYED);

        // 7. DEPLOYED -> VERIFICATION_PENDING
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.DEPLOYED, DeploymentJobStatus.VERIFICATION_PENDING));
        job = stateMachine.transition(job, DeploymentJobStatus.VERIFICATION_PENDING, "Handshake pending", "TEST");
        assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.VERIFICATION_PENDING);

        // 8. VERIFICATION_PENDING -> VERIFIED
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.VERIFICATION_PENDING, DeploymentJobStatus.VERIFIED));
        job = stateMachine.transition(job, DeploymentJobStatus.VERIFIED, "Endpoint verified", "TEST");
        assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.VERIFIED);

        // 9. VERIFIED -> COMPLETED
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.VERIFIED, DeploymentJobStatus.COMPLETED));
        job = stateMachine.transition(job, DeploymentJobStatus.COMPLETED, "Deployment finished", "TEST");
        assertThat(job.getStatus()).isEqualTo(DeploymentJobStatus.COMPLETED);
        assertThat(job.getCompletedAt()).isNotNull();
    }

    // =========================================================================
    // 2. INVALID TRANSITIONS
    // =========================================================================
    @Test
    @DisplayName("Invalid transitions throw InvalidStateTransitionException")
    void testInvalidTransitionsThrowException() {
        // Illegal jump: CREATED -> COMPLETED
        DeploymentJob createdJob = createJob(DeploymentJobStatus.CREATED);
        assertFalse(stateMachine.canTransition(DeploymentJobStatus.CREATED, DeploymentJobStatus.COMPLETED));
        assertThrows(InvalidStateTransitionException.class, () ->
                stateMachine.transition(createdJob, DeploymentJobStatus.COMPLETED, "Illegal jump", "TEST")
        );

        // Retrograde transition: RUNNING -> CREDENTIALS_PENDING
        DeploymentJob runningJob = createJob(DeploymentJobStatus.RUNNING);
        assertFalse(stateMachine.canTransition(DeploymentJobStatus.RUNNING, DeploymentJobStatus.CREDENTIALS_PENDING));
        assertThrows(InvalidStateTransitionException.class, () ->
                stateMachine.transition(runningJob, DeploymentJobStatus.CREDENTIALS_PENDING, "Retrograde", "TEST")
        );

        // Transition out of terminal COMPLETED state: COMPLETED -> RUNNING
        DeploymentJob completedJob = createJob(DeploymentJobStatus.COMPLETED);
        assertFalse(stateMachine.canTransition(DeploymentJobStatus.COMPLETED, DeploymentJobStatus.RUNNING));
        assertThrows(InvalidStateTransitionException.class, () ->
                stateMachine.transition(completedJob, DeploymentJobStatus.RUNNING, "Re-running completed", "TEST")
        );

        // Illegal jump: PLANNED -> VERIFIED
        DeploymentJob plannedJob = createJob(DeploymentJobStatus.PLANNED);
        assertFalse(stateMachine.canTransition(DeploymentJobStatus.PLANNED, DeploymentJobStatus.VERIFIED));
        assertThrows(InvalidStateTransitionException.class, () ->
                stateMachine.transition(plannedJob, DeploymentJobStatus.VERIFIED, "Skipping deployment", "TEST")
        );
    }

    // =========================================================================
    // 3. FAILURE PATHS
    // =========================================================================
    @Test
    @DisplayName("Failure transitions: intermediate states can transition to FAILED and record error")
    void testFailureTransitions() {
        when(deploymentJobRepository.saveAndFlush(any(DeploymentJob.class))).thenAnswer(inv -> inv.getArgument(0));

        // Failure from CREDENTIALS_PENDING
        DeploymentJob job1 = createJob(DeploymentJobStatus.CREDENTIALS_PENDING);
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.CREDENTIALS_PENDING, DeploymentJobStatus.FAILED));
        DeploymentJob failed1 = stateMachine.transition(job1, DeploymentJobStatus.FAILED, "CyberArk timeout", "TEST");
        assertThat(failed1.getStatus()).isEqualTo(DeploymentJobStatus.FAILED);
        assertThat(failed1.getErrorMessage()).isEqualTo("CyberArk timeout");

        // Failure from SENT_TO_MID
        DeploymentJob job2 = createJob(DeploymentJobStatus.SENT_TO_MID);
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.SENT_TO_MID, DeploymentJobStatus.FAILED));
        DeploymentJob failed2 = stateMachine.transition(job2, DeploymentJobStatus.FAILED, "MID Server unreachable", "TEST");
        assertThat(failed2.getStatus()).isEqualTo(DeploymentJobStatus.FAILED);

        // Failure from RUNNING
        DeploymentJob job3 = createJob(DeploymentJobStatus.RUNNING);
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.RUNNING, DeploymentJobStatus.FAILED));
        DeploymentJob failed3 = stateMachine.transition(job3, DeploymentJobStatus.FAILED, "IIS binding error", "TEST");
        assertThat(failed3.getStatus()).isEqualTo(DeploymentJobStatus.FAILED);
    }

    // =========================================================================
    // 4. RETRY PATHS
    // =========================================================================
    @Test
    @DisplayName("Retry transitions: FAILED -> RETRY_PENDING -> CREDENTIALS_PENDING increments retry count")
    void testRetryTransitions() {
        when(deploymentJobRepository.saveAndFlush(any(DeploymentJob.class))).thenAnswer(inv -> inv.getArgument(0));

        DeploymentJob job = createJob(DeploymentJobStatus.FAILED);
        assertThat(job.getRetryCount()).isZero();

        // FAILED -> RETRY_PENDING
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.FAILED, DeploymentJobStatus.RETRY_PENDING));
        DeploymentJob retryJob = stateMachine.transition(job, DeploymentJobStatus.RETRY_PENDING, "Scheduling retry", "TEST");
        assertThat(retryJob.getStatus()).isEqualTo(DeploymentJobStatus.RETRY_PENDING);
        assertThat(retryJob.getRetryCount()).isEqualTo(1);

        // RETRY_PENDING -> CREDENTIALS_PENDING
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.RETRY_PENDING, DeploymentJobStatus.CREDENTIALS_PENDING));
        DeploymentJob resumed = stateMachine.transition(retryJob, DeploymentJobStatus.CREDENTIALS_PENDING, "Re-executing", "TEST");
        assertThat(resumed.getStatus()).isEqualTo(DeploymentJobStatus.CREDENTIALS_PENDING);
    }

    // =========================================================================
    // 5. MANUAL REVIEW PATHS
    // =========================================================================
    @Test
    @DisplayName("Manual review transitions: FAILED -> MANUAL_REVIEW -> RETRY_PENDING or COMPLETED")
    void testManualReviewTransitions() {
        when(deploymentJobRepository.saveAndFlush(any(DeploymentJob.class))).thenAnswer(inv -> inv.getArgument(0));

        DeploymentJob job = createJob(DeploymentJobStatus.FAILED);

        // FAILED -> MANUAL_REVIEW
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.FAILED, DeploymentJobStatus.MANUAL_REVIEW));
        DeploymentJob reviewJob = stateMachine.transition(job, DeploymentJobStatus.MANUAL_REVIEW, "Max retries exceeded", "TEST");
        assertThat(reviewJob.getStatus()).isEqualTo(DeploymentJobStatus.MANUAL_REVIEW);

        // MANUAL_REVIEW -> RETRY_PENDING (Admin unblocks)
        assertTrue(stateMachine.canTransition(DeploymentJobStatus.MANUAL_REVIEW, DeploymentJobStatus.RETRY_PENDING));
        DeploymentJob approvedJob = stateMachine.transition(reviewJob, DeploymentJobStatus.RETRY_PENDING, "Admin approved retry", "admin");
        assertThat(approvedJob.getStatus()).isEqualTo(DeploymentJobStatus.RETRY_PENDING);
    }

    // =========================================================================
    // 6. IDEMPOTENT NO-OP
    // =========================================================================
    @Test
    @DisplayName("Idempotent transition to same state returns job unchanged without error")
    void testIdempotentSameStateTransition() {
        DeploymentJob job = createJob(DeploymentJobStatus.RUNNING);
        DeploymentJob result = stateMachine.transition(job, DeploymentJobStatus.RUNNING, "Redundant transition", "TEST");
        assertThat(result.getStatus()).isEqualTo(DeploymentJobStatus.RUNNING);
    }
}
