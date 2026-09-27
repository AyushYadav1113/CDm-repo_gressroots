package com.grassroots.cdm.workflow.scheduler;

import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.deployment.workflow.DeploymentWorkflowService;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.metrics.CdmMetricsService;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import com.grassroots.cdm.workflow.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DeploymentRetryScheduler.
 */
@ExtendWith(MockitoExtension.class)
class DeploymentRetrySchedulerTest {

    @Mock DeploymentJobRepository deploymentJobRepository;
    @Mock WorkflowEngine workflowEngine;
    @Mock DeploymentWorkflowService workflowService;
    @Mock AuditService auditService;
    @Mock CdmMetricsService metricsService;

    private DeploymentRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DeploymentRetryScheduler(
                deploymentJobRepository, workflowEngine, workflowService, auditService, metricsService);
    }

    @Test
    void runScheduledRetries_does_nothing_when_no_retry_pending_jobs() {
        when(deploymentJobRepository.findByStatus(DeploymentJobStatus.RETRY_PENDING)).thenReturn(List.of());

        scheduler.runScheduledRetries();

        // With an empty list we return early — no workflow, no metric, no audit calls
        verify(workflowEngine, never()).retryJobWorkflow(any());
        verify(metricsService, never()).recordRetryAttempt(any(Integer.class));
    }

    @Test
    void runScheduledRetries_executes_workflow_when_scheduledAt_is_in_past() {
        DeploymentJob job = buildJob(1, 3, Instant.now().minusSeconds(60));
        when(deploymentJobRepository.findByStatus(DeploymentJobStatus.RETRY_PENDING)).thenReturn(List.of(job));

        scheduler.runScheduledRetries();

        verify(workflowEngine, times(1)).retryJobWorkflow(job.getId());
        verify(metricsService, times(1)).recordRetryAttempt(1);
    }

    @Test
    void runScheduledRetries_skips_job_when_scheduledAt_is_in_future() {
        DeploymentJob job = buildJob(1, 3, Instant.now().plusSeconds(300));
        when(deploymentJobRepository.findByStatus(DeploymentJobStatus.RETRY_PENDING)).thenReturn(List.of(job));

        scheduler.runScheduledRetries();

        verify(workflowEngine, never()).retryJobWorkflow(any());
        verify(metricsService, never()).recordRetryAttempt(any(Integer.class));
    }

    @Test
    void runScheduledRetries_escalates_when_attemptCount_equals_maxRetries() {
        DeploymentJob job = buildJob(3, 3, Instant.now().minusSeconds(60));
        job.setErrorMessage("MID Server unavailable");
        when(deploymentJobRepository.findByStatus(DeploymentJobStatus.RETRY_PENDING)).thenReturn(List.of(job));

        scheduler.runScheduledRetries();

        verify(workflowService, times(1)).escalateToManualReview(any(UUID.class), anyString(), anyString());
        verify(workflowEngine, never()).retryJobWorkflow(any());
        verify(metricsService, never()).recordRetryAttempt(any(Integer.class));
    }

    @Test
    void runScheduledRetries_escalates_when_attemptCount_exceeds_maxRetries() {
        DeploymentJob job = buildJob(5, 3, Instant.now().minusSeconds(60));
        when(deploymentJobRepository.findByStatus(DeploymentJobStatus.RETRY_PENDING)).thenReturn(List.of(job));

        scheduler.runScheduledRetries();

        verify(workflowService, times(1)).escalateToManualReview(any(UUID.class), anyString(), anyString());
        verify(workflowEngine, never()).retryJobWorkflow(any());
    }

    @Test
    void runScheduledRetries_tolerates_exception_per_job_and_continues_to_next() {
        DeploymentJob job1 = buildJob(1, 3, Instant.now().minusSeconds(60));
        DeploymentJob job2 = buildJob(1, 3, Instant.now().minusSeconds(60));

        when(deploymentJobRepository.findByStatus(DeploymentJobStatus.RETRY_PENDING))
                .thenReturn(List.of(job1, job2));
        doThrow(new RuntimeException("MID timeout")).when(workflowEngine).retryJobWorkflow(job1.getId());
        doNothing().when(workflowEngine).retryJobWorkflow(job2.getId());

        assertThatNoException().isThrownBy(() -> scheduler.runScheduledRetries());

        verify(workflowEngine, times(1)).retryJobWorkflow(job1.getId());
        verify(workflowEngine, times(1)).retryJobWorkflow(job2.getId());
    }

    @Test
    void runScheduledRetries_records_workflow_duration_after_processing() {
        when(deploymentJobRepository.findByStatus(DeploymentJobStatus.RETRY_PENDING))
                .thenReturn(List.of(buildJob(1, 3, Instant.now().minusSeconds(10))));

        scheduler.runScheduledRetries();

        verify(metricsService, times(1)).recordWorkflowDuration(anyString(), any(Duration.class));
    }

    @Test
    void runScheduledRetries_does_not_record_duration_for_empty_list_early_return() {
        when(deploymentJobRepository.findByStatus(DeploymentJobStatus.RETRY_PENDING)).thenReturn(List.of());

        scheduler.runScheduledRetries();

        // Early return — duration is not recorded
        verify(metricsService, never()).recordWorkflowDuration(anyString(), any(Duration.class));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private DeploymentJob buildJob(int attempts, int maxRetries, Instant scheduledAt) {
        DeploymentJob job = new DeploymentJob();
        job.setId(UUID.randomUUID());
        job.setAttemptCount(attempts);
        job.setMaxRetries(maxRetries);
        job.setScheduledAt(scheduledAt);
        job.setStatus(DeploymentJobStatus.RETRY_PENDING);
        return job;
    }
}
