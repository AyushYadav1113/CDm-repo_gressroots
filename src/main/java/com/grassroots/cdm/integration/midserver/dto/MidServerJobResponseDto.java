package com.grassroots.cdm.integration.midserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.grassroots.cdm.entity.enums.MidServerExecutionState;

import java.time.Instant;
import java.util.UUID;

/**
 * Receipt returned by the MID Server when an execution job is accepted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MidServerJobResponseDto {

    private String taskId;
    private UUID jobId;
    private String idempotencyKey;
    private MidServerExecutionState status = MidServerExecutionState.QUEUED;
    private Instant acceptedAt;
    private String message;

    public MidServerJobResponseDto() {
    }

    public MidServerJobResponseDto(String taskId, UUID jobId, String idempotencyKey,
                                   MidServerExecutionState status, Instant acceptedAt, String message) {
        this.taskId = taskId;
        this.jobId = jobId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.acceptedAt = acceptedAt != null ? acceptedAt : Instant.now();
        this.message = message;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public MidServerExecutionState getStatus() {
        return status;
    }

    public void setStatus(MidServerExecutionState status) {
        this.status = status;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "MidServerJobResponseDto{" +
                "taskId='" + taskId + '\'' +
                ", jobId=" + jobId +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", status=" + status +
                ", acceptedAt=" + acceptedAt +
                '}';
    }
}
