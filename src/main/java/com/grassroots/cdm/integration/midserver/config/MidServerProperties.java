package com.grassroots.cdm.integration.midserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cdm.midserver")
public class MidServerProperties {

    public enum AuthType {
        BEARER,
        BASIC,
        API_KEY,
        NONE
    }

    private String baseUrl = "https://mid-server.grassroots.internal:8443";
    private AuthType authType = AuthType.BEARER;
    private String bearerToken;
    private String username;
    private String password;
    private String apiKey;
    private String apiKeyHeader = "X-MID-API-KEY";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private int maxRetries = 3;
    private long backoffMs = 1000L;
    private boolean mockEnabled = false;
    private String jobsPath = "/api/v1/mid/jobs";
    private int executionTimeoutSeconds = 300;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public void setAuthType(AuthType authType) {
        this.authType = authType;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKeyHeader() {
        return apiKeyHeader;
    }

    public void setApiKeyHeader(String apiKeyHeader) {
        this.apiKeyHeader = apiKeyHeader;
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

    public long getBackoffMs() {
        return backoffMs;
    }

    public void setBackoffMs(long backoffMs) {
        this.backoffMs = backoffMs;
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public String getJobsPath() {
        return jobsPath;
    }

    public void setJobsPath(String jobsPath) {
        this.jobsPath = jobsPath;
    }

    public int getExecutionTimeoutSeconds() {
        return executionTimeoutSeconds;
    }

    public void setExecutionTimeoutSeconds(int executionTimeoutSeconds) {
        this.executionTimeoutSeconds = executionTimeoutSeconds;
    }
}
