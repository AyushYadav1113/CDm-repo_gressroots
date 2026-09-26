package com.grassroots.cdm.integration;

/**
 * Interface contract for ServiceNow MID Server orchestration dispatch.
 *
 * Adheres strictly to the architectural constraint:
 * CDM is the orchestration/decision-making layer.
 * MID Server is the execution layer.
 * Target servers must never be directly controlled by arbitrary commands from the CDM backend.
 */
public interface MidServerClient {

    /**
     * Submits a structured deployment job instruction payload to the MID Server queue.
     *
     * @param payload Structured execution parameters
     * @return Dispatch receipt containing MID server queue task ID
     */
    MidServerTaskReceipt dispatchDeploymentTask(MidServerJobPayload payload);

    /**
     * Queries the status of a previously dispatched MID Server task.
     *
     * @param midServerTaskId Unique task ID from dispatch
     * @return Current task execution state
     */
    MidServerTaskStatus queryTaskStatus(String midServerTaskId);

    record MidServerJobPayload(
            String jobReference,
            String targetType,
            String targetHost,
            int targetPort,
            String deploymentOperation,
            String payloadHash
    ) {}

    record MidServerTaskReceipt(
            String taskId,
            String status,
            String submittedAt
    ) {}

    record MidServerTaskStatus(
            String taskId,
            String state, // READY, PROCESSING, COMPLETED, ERROR
            int exitCode,
            String outputSummary
    ) {}
}
