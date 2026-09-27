package com.grassroots.cdm.integration.midserver;

import com.grassroots.cdm.integration.midserver.dto.MidServerCancelResponseDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerHealthDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobRequestDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobResponseDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerStatusQueryResponseDto;

/**
 * Interface contract for ServiceNow MID Server orchestration client.
 *
 * Adheres strictly to the architectural boundary:
 * - CDM is responsible for orchestration, decisions, lifecycle tracking, and policy enforcement.
 * - MID Server is responsible for execution within the target enterprise network.
 */
public interface MidServerClient {

    /**
     * Dispatches a structured deployment job instruction to the default configured MID Server endpoint.
     *
     * @param request Validated job execution payload
     * @return Dispatch receipt containing the MID task identifier
     */
    MidServerJobResponseDto submitJob(MidServerJobRequestDto request);

    /**
     * Dispatches a structured deployment job instruction to a specific MID Server node endpoint.
     *
     * @param endpoint Target MID Server URL (e.g., https://mid01.internal:8443)
     * @param request Validated job execution payload
     * @return Dispatch receipt containing the MID task identifier
     */
    MidServerJobResponseDto submitJob(String endpoint, MidServerJobRequestDto request);

    /**
     * Queries the execution status and output logs of a dispatched MID Server task using default endpoint.
     *
     * @param taskId Unique task reference ID from the MID Server
     * @return Current task execution state and output telemetry
     */
    MidServerStatusQueryResponseDto getJobStatus(String taskId);

    /**
     * Queries the execution status and output logs of a dispatched MID Server task on a specific node.
     *
     * @param endpoint Target MID Server URL
     * @param taskId Unique task reference ID from the MID Server
     * @return Current task execution state and output telemetry
     */
    MidServerStatusQueryResponseDto getJobStatus(String endpoint, String taskId);

    /**
     * Requests cancellation of an in-progress or queued MID Server task using default endpoint.
     *
     * @param taskId Unique task reference ID
     * @param reason Operator or system justification
     * @return Cancellation outcome receipt
     */
    MidServerCancelResponseDto cancelJob(String taskId, String reason);

    /**
     * Requests cancellation of an in-progress or queued MID Server task on a specific node.
     *
     * @param endpoint Target MID Server URL
     * @param taskId Unique task reference ID
     * @param reason Operator or system justification
     * @return Cancellation outcome receipt
     */
    MidServerCancelResponseDto cancelJob(String endpoint, String taskId, String reason);

    /**
     * Verifies connectivity and health of the default MID Server endpoint.
     *
     * @return true if the MID Server responds healthy, false otherwise
     */
    boolean checkHealth();

    /**
     * Verifies connectivity and health of a specific MID Server endpoint.
     *
     * @param endpoint Target MID Server URL
     * @return true if the MID Server responds healthy, false otherwise
     */
    boolean checkHealth(String endpoint);

    /**
     * Fetches detailed health telemetry from default endpoint.
     */
    MidServerHealthDto getHealthDetails();

    /**
     * Fetches detailed health telemetry from a specific endpoint.
     */
    MidServerHealthDto getHealthDetails(String endpoint);
}
