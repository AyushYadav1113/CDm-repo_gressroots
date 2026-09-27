package com.grassroots.cdm.integration.midserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.grassroots.cdm.entity.enums.MidServerExecutionState;

import java.time.Instant;
import java.util.UUID;

/**
 * Execution telemetry and execution state returned by MID Server during polling / status query.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MidServerStatusQueryResponseDto {

    private String taskId;
    private UUID jobId;
    private String idempotencyKey;
    private MidServerExecutionState state;
    private Integer exitCode;
    private String stdoutSummary;
    private String stderrSummary;
    private String errorMessage;
    private Instant dispatchedAt;
    private Instant startedAt;
    private Instant completedAt;

    public MidServerStatusQueryResponseDto() {
    }

    public MidServerStatusQueryResponseDto(String taskId, UUID jobId, String idempotencyKey,
                                          MidServerExecutionState state, Integer exitCode,
                                          String stdoutSummary, String stderrSummary, String errorMessage,
                                          Instant dispatchedAt, Instant startedAt, Instant completedAt) {
        this.taskId = taskId;
        this.jobId = jobId;
        this.idempotencyKey = idempotencyKey;
        this.state = state;
        this.exitCode = exitCode;
        this.stdoutSummary = stdoutSummary;
        this.stderrSummary = stderrSummary;
        this.errorMessage = errorMessage;
        this.dispatchedAt = dispatchedAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
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

    public MidServerExecutionState getState() {
        return state;
    }

    public void setState(MidServerExecutionState state) {
        this.state = state;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public String getStdoutSummary() {
        return stdoutSummary;
    }

    public void setStdoutSummary(String stdoutSummary) {
        this.stdoutSummary = stdoutSummary;
    }

    public String getStderrSummary() {
        return stderrSummary;
    }

    public void setStderrSummary(String stderrSummary) {
        this.stderrSummary = stderrSummary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isFinished() {
        return state == MidServerExecutionState.SUCCESS
                || state == MidServerExecutionState.FAILED
                || state == MidServerExecutionState.TIMED_OUT
                || state == MidServerExecutionState.CANCELLED;
    }

    public boolean isSuccessful() {
        return state == MidServerExecutionState.SUCCESS && (exitCode == null || exitCode == 0);
    }

    @Override
    public String toString() {
        return "MidServerStatusQueryResponseDto{" +
                "taskId='" + taskId + '\'' +
                ", jobId=" + jobId +
                ", state=" + state +
                ", exitCode=" + exitCode +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                '}';
    }
}
