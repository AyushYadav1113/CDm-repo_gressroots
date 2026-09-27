package com.grassroots.cdm.integration.midserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Cancellation confirmation returned by MID Server.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MidServerCancelResponseDto {

    private String taskId;
    private String status;
    private Instant cancelledAt;
    private String message;

    public MidServerCancelResponseDto() {
    }

    public MidServerCancelResponseDto(String taskId, String status, Instant cancelledAt, String message) {
        this.taskId = taskId;
        this.status = status;
        this.cancelledAt = cancelledAt != null ? cancelledAt : Instant.now();
        this.message = message;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
