package com.grassroots.cdm.credential.dto;

import java.util.Objects;

/**
 * Parameters for on-demand credential retrieval from CyberArk vault.
 */
public class CredentialRequest {

    private String safeName;
    private String accountName;
    private String targetHost;
    private String reason;
    private int timeoutMs = 5000;

    public CredentialRequest() {
    }

    public CredentialRequest(String safeName, String accountName, String targetHost, String reason, int timeoutMs) {
        this.safeName = safeName;
        this.accountName = accountName;
        this.targetHost = targetHost;
        this.reason = reason;
        this.timeoutMs = timeoutMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSafeName() {
        return safeName;
    }

    public void setSafeName(String safeName) {
        this.safeName = safeName;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public void setTargetHost(String targetHost) {
        this.targetHost = targetHost;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String toString() {
        return "CredentialRequest[safe=" + safeName + ", account=" + accountName
                + ", targetHost=" + targetHost + ", reason=" + reason + "]";
    }

    public static class Builder {
        private String safeName;
        private String accountName;
        private String targetHost;
        private String reason = "Automated certificate deployment execution";
        private int timeoutMs = 5000;

        public Builder safeName(String safeName) {
            this.safeName = safeName;
            return this;
        }

        public Builder accountName(String accountName) {
            this.accountName = accountName;
            return this;
        }

        public Builder targetHost(String targetHost) {
            this.targetHost = targetHost;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder timeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public CredentialRequest build() {
            Objects.requireNonNull(safeName, "safeName cannot be null");
            Objects.requireNonNull(accountName, "accountName cannot be null");
            return new CredentialRequest(safeName, accountName, targetHost, reason, timeoutMs);
        }
    }
}
