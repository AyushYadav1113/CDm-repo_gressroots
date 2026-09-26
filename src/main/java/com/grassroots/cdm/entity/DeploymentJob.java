package com.grassroots.cdm.entity;

import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.entity.enums.DeploymentType;
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
 * Entity tracking the end-to-end orchestration lifecycle of certificate deployments.
 */
@Entity
@Table(name = "deployment_jobs")
public class DeploymentJob extends BaseEntity {

    @Column(name = "job_reference", nullable = false, unique = true, length = 64)
    private String jobReference;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_certificate_id")
    private CertificateRecord oldCertificate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_certificate_id", nullable = false)
    private CertificateRecord newCertificate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_server_id")
    private TargetServer targetServer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installation_id")
    private CertificateInstallation installation;

    @Column(name = "target_host")
    private String targetHost;

    @Column(name = "target_port")
    private Integer targetPort = 443;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_type", nullable = false, length = 50)
    private DeploymentType deploymentType = DeploymentType.RENEWAL_REPLACEMENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private DeploymentJobStatus status = DeploymentJobStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @Column(name = "error_information", columnDefinition = "TEXT")
    private String errorInformation;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "mid_server_task_id")
    private String midServerTaskId;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public DeploymentJob() {
    }

    public String getJobReference() {
        return jobReference;
    }

    public void setJobReference(String jobReference) {
        this.jobReference = jobReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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

    public TargetServer getTargetServer() {
        return targetServer;
    }

    public void setTargetServer(TargetServer targetServer) {
        this.targetServer = targetServer;
        if (targetServer != null) {
            if (this.targetHost == null) {
                this.targetHost = targetServer.getHostname();
            }
            if (this.targetType == null && targetServer.getTechnology() != null) {
                this.targetType = targetServer.getTechnology().name();
            }
        }
    }

    public CertificateInstallation getInstallation() {
        return installation;
    }

    public void setInstallation(CertificateInstallation installation) {
        this.installation = installation;
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

    public DeploymentType getDeploymentType() {
        return deploymentType;
    }

    public void setDeploymentType(DeploymentType deploymentType) {
        this.deploymentType = deploymentType;
    }

    public DeploymentJobStatus getStatus() {
        return status;
    }

    public void setStatus(DeploymentJobStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
        this.retryCount = attemptCount;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
        this.attemptCount = retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getErrorInformation() {
        return errorInformation;
    }

    public void setErrorInformation(String errorInformation) {
        this.errorInformation = errorInformation;
        this.errorMessage = errorInformation;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.errorInformation = errorMessage;
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

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
        if (this.dispatchedAt == null) {
            this.dispatchedAt = startedAt;
        }
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
        if (this.startedAt == null) {
            this.startedAt = dispatchedAt;
        }
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
