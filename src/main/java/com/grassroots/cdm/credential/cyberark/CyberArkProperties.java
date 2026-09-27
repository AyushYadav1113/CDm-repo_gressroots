package com.grassroots.cdm.credential.cyberark;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for CyberArk Central Credential Provider (CCP) integration.
 */
@Component
@ConfigurationProperties(prefix = "cdm.cyberark")
public class CyberArkProperties {

    private boolean enabled = true;
    private String baseUrl = "https://cyberark.internal/AIMWebService";
    private String appId = "CDM_Application";
    private String defaultSafe = "GrassrootsSafe";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private int maxRetries = 3;
    private int retryBackoffMs = 1000;
    private boolean mockEnabled = false;
    private String clientCertPath;
    private String clientCertPassword;

    public CyberArkProperties() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getDefaultSafe() {
        return defaultSafe;
    }

    public void setDefaultSafe(String defaultSafe) {
        this.defaultSafe = defaultSafe;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(int retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public String getClientCertPath() {
        return clientCertPath;
    }

    public void setClientCertPath(String clientCertPath) {
        this.clientCertPath = clientCertPath;
    }

    public String getClientCertPassword() {
        return clientCertPassword;
    }

    public void setClientCertPassword(String clientCertPassword) {
        this.clientCertPassword = clientCertPassword;
    }

    @Override
    public String toString() {
        return "CyberArkProperties[enabled=" + enabled + ", baseUrl=" + baseUrl
                + ", appId=" + appId + ", defaultSafe=" + defaultSafe
                + ", connectTimeoutMs=" + connectTimeoutMs + ", readTimeoutMs=" + readTimeoutMs
                + ", maxRetries=" + maxRetries + ", mockEnabled=" + mockEnabled
                + ", clientCertPassword=[REDACTED]]";
    }
}
