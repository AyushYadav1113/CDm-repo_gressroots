package com.grassroots.cdm.deployment.adapter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Detailed outcome returned by a technology-specific deployment adapter.
 */
public class DeploymentAdapterResult {

    public enum Status {
        SUCCESS,
        FAILED,
        ROLLED_BACK,
        SKIPPED_IDEMPOTENT
    }

    private final UUID jobId;
    private final String idempotencyKey;
    private final String targetHost;
    private final int targetPort;
    private final Status status;
    private final String deployedThumbprint;
    private final String verifiedThumbprint;
    private final boolean rollbackExecuted;
    private final boolean idempotent;
    private final String errorMessage;
    private final String midServerTaskId;
    private final List<DeploymentStepResult> stepResults;
    private final Instant timestamp;

    public DeploymentAdapterResult(Builder builder) {
        this.jobId = builder.jobId;
        this.idempotencyKey = builder.idempotencyKey;
        this.targetHost = builder.targetHost;
        this.targetPort = builder.targetPort;
        this.status = builder.status;
        this.deployedThumbprint = builder.deployedThumbprint;
        this.verifiedThumbprint = builder.verifiedThumbprint;
        this.rollbackExecuted = builder.rollbackExecuted;
        this.idempotent = builder.idempotent;
        this.errorMessage = builder.errorMessage;
        this.midServerTaskId = builder.midServerTaskId;
        this.stepResults = Collections.unmodifiableList(new ArrayList<>(builder.stepResults));
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
    }

    public UUID getJobId() {
        return jobId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public Status getStatus() {
        return status;
    }

    public String getDeployedThumbprint() {
        return deployedThumbprint;
    }

    public String getVerifiedThumbprint() {
        return verifiedThumbprint;
    }

    public boolean isRollbackExecuted() {
        return rollbackExecuted;
    }

    public boolean isIdempotent() {
        return idempotent;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getMidServerTaskId() {
        return midServerTaskId;
    }

    public List<DeploymentStepResult> getStepResults() {
        return stepResults;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public boolean isSuccessful() {
        return status == Status.SUCCESS || status == Status.SKIPPED_IDEMPOTENT;
    }

    public static Builder builder(UUID jobId, String idempotencyKey) {
        return new Builder(jobId, idempotencyKey);
    }

    public static class Builder {
        private final UUID jobId;
        private final String idempotencyKey;
        private String targetHost;
        private int targetPort = 443;
        private Status status = Status.SUCCESS;
        private String deployedThumbprint;
        private String verifiedThumbprint;
        private boolean rollbackExecuted = false;
        private boolean idempotent = false;
        private String errorMessage;
        private String midServerTaskId;
        private final List<DeploymentStepResult> stepResults = new ArrayList<>();
        private Instant timestamp;

        public Builder(UUID jobId, String idempotencyKey) {
            this.jobId = jobId;
            this.idempotencyKey = idempotencyKey;
        }

        public Builder targetHost(String targetHost) {
            this.targetHost = targetHost;
            return this;
        }

        public Builder targetPort(int targetPort) {
            this.targetPort = targetPort;
            return this;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder deployedThumbprint(String deployedThumbprint) {
            this.deployedThumbprint = deployedThumbprint;
            return this;
        }

        public Builder verifiedThumbprint(String verifiedThumbprint) {
            this.verifiedThumbprint = verifiedThumbprint;
            return this;
        }

        public Builder rollbackExecuted(boolean rollbackExecuted) {
            this.rollbackExecuted = rollbackExecuted;
            return this;
        }

        public Builder idempotent(boolean idempotent) {
            this.idempotent = idempotent;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder midServerTaskId(String midServerTaskId) {
            this.midServerTaskId = midServerTaskId;
            return this;
        }

        public Builder addStepResult(DeploymentStepResult stepResult) {
            this.stepResults.add(stepResult);
            return this;
        }

        public Builder addStepResults(List<DeploymentStepResult> stepResults) {
            if (stepResults != null) {
                this.stepResults.addAll(stepResults);
            }
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public DeploymentAdapterResult build() {
            return new DeploymentAdapterResult(this);
        }
    }
}
