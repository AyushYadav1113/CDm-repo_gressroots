package com.grassroots.cdm.entity;

import com.grassroots.cdm.entity.enums.MidServerExecutionState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entity persisting MID Server execution task instances, execution telemetry, and lifecycle status.
 */
@Entity
@Table(name = "mid_server_executions")
public class MidServerExecution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private DeploymentJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mid_server_id")
    private MidServer midServer;

    @Column(name = "task_id", nullable = false, length = 100)
    private String taskId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private MidServerExecutionState status = MidServerExecutionState.QUEUED;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "stdout_summary", columnDefinition = "TEXT")
    private String stdoutSummary;

    @Column(name = "stderr_summary", columnDefinition = "TEXT")
    private String stderrSummary;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "dispatched_at", nullable = false)
    private Instant dispatchedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public MidServerExecution() {
    }

    public MidServerExecution(DeploymentJob job, MidServer midServer, String taskId, String idempotencyKey, Instant dispatchedAt) {
        this.job = job;
        this.midServer = midServer;
        this.taskId = taskId;
        this.idempotencyKey = idempotencyKey;
        this.dispatchedAt = dispatchedAt;
        this.status = MidServerExecutionState.QUEUED;
    }

    public DeploymentJob getJob() {
        return job;
    }

    public void setJob(DeploymentJob job) {
        this.job = job;
    }

    public MidServer getMidServer() {
        return midServer;
    }

    public void setMidServer(MidServer midServer) {
        this.midServer = midServer;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
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
}
