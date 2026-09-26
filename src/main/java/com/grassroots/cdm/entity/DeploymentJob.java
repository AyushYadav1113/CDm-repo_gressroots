package com.grassroots.cdm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entity tracking deployment workflow lifecycle dispatched to MID Servers.
 */
@Entity
@Table(name = "deployment_jobs")
public class DeploymentJob extends BaseEntity {

    @Column(name = "job_reference", nullable = false, unique = true, length = 64)
    private String jobReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_certificate_id")
    private CertificateRecord oldCertificate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_certificate_id")
    private CertificateRecord newCertificate;

    @Column(name = "target_host", nullable = false)
    private String targetHost;

    @Column(name = "target_port", nullable = false)
    private int targetPort = 443;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "PENDING";

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "mid_server_task_id")
    private String midServerTaskId;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public String getJobReference() {
        return jobReference;
    }

    public void setJobReference(String jobReference) {
        this.jobReference = jobReference;
    }

    public CertificateRecord getOldCertificate() {
        return oldCertificate;
    }

    public void setOldCertificate(CertificateRecord oldCertificate) {
        this.oldCertificate = oldCertificate;
    }

    public CertificateRecord getNewCertificate() {
        return newCertificate;
    }

    public void setNewCertificate(CertificateRecord newCertificate) {
        this.newCertificate = newCertificate;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public void setTargetHost(String targetHost) {
        this.targetHost = targetHost;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(int targetPort) {
        this.targetPort = targetPort;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getMidServerTaskId() {
        return midServerTaskId;
    }

    public void setMidServerTaskId(String midServerTaskId) {
        this.midServerTaskId = midServerTaskId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
