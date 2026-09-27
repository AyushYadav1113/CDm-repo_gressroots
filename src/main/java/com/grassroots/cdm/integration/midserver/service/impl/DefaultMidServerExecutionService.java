package com.grassroots.cdm.integration.midserver.service.impl;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.MidServerExecution;
import com.grassroots.cdm.entity.enums.MidServerExecutionState;
import com.grassroots.cdm.integration.midserver.MidServerClient;
import com.grassroots.cdm.integration.midserver.dto.MidServerStatusQueryResponseDto;
import com.grassroots.cdm.integration.midserver.exception.MidServerException;
import com.grassroots.cdm.integration.midserver.service.MidServerExecutionService;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import com.grassroots.cdm.repository.MidServerExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DefaultMidServerExecutionService implements MidServerExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMidServerExecutionService.class);

    private final MidServerExecutionRepository executionRepository;
    private final DeploymentJobRepository deploymentJobRepository;
    private final MidServerClient midServerClient;
    private final AuditService auditService;

    public DefaultMidServerExecutionService(
            MidServerExecutionRepository executionRepository,
            DeploymentJobRepository deploymentJobRepository,
            MidServerClient midServerClient,
            AuditService auditService) {
        this.executionRepository = executionRepository;
        this.deploymentJobRepository = deploymentJobRepository;
        this.midServerClient = midServerClient;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public MidServerExecution recordDispatch(DeploymentJob job, MidServer midServer, String taskId, String idempotencyKey) {
        log.info("Persisting MID Server execution record [jobId={}, taskId={}, idempotencyKey={}]",
                job.getId(), taskId, idempotencyKey);

        MidServerExecution execution = new MidServerExecution(job, midServer, taskId, idempotencyKey, Instant.now());
        execution.setStatus(MidServerExecutionState.QUEUED);
        MidServerExecution saved = executionRepository.saveAndFlush(execution);

        auditService.recordAudit(AuditEvent.create(
                AuditAction.DEPLOYMENT_DISPATCHED,
                "MidServerExecution",
                saved.getId().toString(),
                "SYSTEM",
                "SUCCESS",
                "Dispatched execution task to MID Server (taskId=" + taskId + ")",
                null
        ));

        return saved;
    }

    @Override
    @Transactional
    public MidServerExecution updateExecutionStatus(String taskId, MidServerStatusQueryResponseDto statusDto) {
        MidServerExecution execution = executionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new MidServerException("No execution record found for taskId: " + taskId));

        execution.setStatus(statusDto.getState());
        execution.setExitCode(statusDto.getExitCode());
        execution.setStdoutSummary(statusDto.getStdoutSummary());
        execution.setStderrSummary(statusDto.getStderrSummary());
        execution.setErrorMessage(statusDto.getErrorMessage());

        if (statusDto.getStartedAt() != null) {
            execution.setStartedAt(statusDto.getStartedAt());
        }
        if (statusDto.getCompletedAt() != null) {
            execution.setCompletedAt(statusDto.getCompletedAt());
        }

        MidServerExecution saved = executionRepository.saveAndFlush(execution);

        // Update corresponding DeploymentJob if completed or started
        DeploymentJob job = execution.getJob();
        if (job != null) {
            if (statusDto.getStartedAt() != null && job.getStartedAt() == null) {
                job.setStartedAt(statusDto.getStartedAt());
            }
            if (statusDto.getErrorMessage() != null) {
                job.setErrorMessage(statusDto.getErrorMessage());
            }
            if (statusDto.getState() == MidServerExecutionState.FAILED) {
                job.setErrorInformation(statusDto.getStderrSummary() != null ? statusDto.getStderrSummary() : statusDto.getErrorMessage());
            }
            deploymentJobRepository.saveAndFlush(job);
        }

        auditService.recordAudit(AuditEvent.create(
                statusDto.isSuccessful() ? AuditAction.LIVE_ENDPOINT_VERIFIED : AuditAction.JOB_FAILED,
                "MidServerExecution",
                saved.getId().toString(),
                "SYSTEM",
                statusDto.isSuccessful() ? "SUCCESS" : "FAILURE",
                "MID Task " + taskId + " state=" + statusDto.getState() + ", exitCode=" + statusDto.getExitCode(),
                null
        ));

        return saved;
    }

    @Override
    @Transactional
    public MidServerStatusQueryResponseDto pollAndSyncTaskStatus(String taskId) {
        MidServerExecution execution = executionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new MidServerException("No execution record found for taskId: " + taskId));

        String endpoint = execution.getMidServer() != null ? execution.getMidServer().getEndpoint() : null;
        MidServerStatusQueryResponseDto statusDto = midServerClient.getJobStatus(endpoint, taskId);

        updateExecutionStatus(taskId, statusDto);
        return statusDto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MidServerExecution> getExecutionsForJob(UUID jobId) {
        return executionRepository.findByJobIdOrderByDispatchedAtDesc(jobId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MidServerExecution> findByTaskId(String taskId) {
        return executionRepository.findByTaskId(taskId);
    }
}
