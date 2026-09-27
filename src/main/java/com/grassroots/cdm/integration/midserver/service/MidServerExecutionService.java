package com.grassroots.cdm.integration.midserver.service;

import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.MidServerExecution;
import com.grassroots.cdm.integration.midserver.dto.MidServerStatusQueryResponseDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing persistence, status polling, and audit synchronization of MID Server tasks.
 */
public interface MidServerExecutionService {

    /**
     * Persists an initial MID execution record upon dispatch.
     */
    MidServerExecution recordDispatch(DeploymentJob job, MidServer midServer, String taskId, String idempotencyKey);

    /**
     * Updates an existing execution record based on a status query response from the MID Server.
     */
    MidServerExecution updateExecutionStatus(String taskId, MidServerStatusQueryResponseDto statusDto);

    /**
     * Queries the MID Server for current task status and persists updates in CDM.
     */
    MidServerStatusQueryResponseDto pollAndSyncTaskStatus(String taskId);

    /**
     * Retrieves historical execution records for a deployment job.
     */
    List<MidServerExecution> getExecutionsForJob(UUID jobId);

    /**
     * Finds latest execution by MID task ID.
     */
    Optional<MidServerExecution> findByTaskId(String taskId);
}
