package com.grassroots.cdm.integration.servicenow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalized configuration properties for ServiceNow Table API and discovery integration.
 * Sensitive fields (passwords, tokens, client secrets) are loaded strictly from environment or secret stores.
 */
@Configuration
@ConfigurationProperties(prefix = "cdm.servicenow")
public class ServiceNowProperties {

    private String baseUrl = "https://example.service-now.com";
    private String table = "cmdb_ci_certificate";
    private String username;
    private String password;
    private String clientId;
    private String clientSecret;
    private String bearerToken;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private int maxRetries = 3;
    private long backoffMs = 1000;
    private String queryFilter = "active=true^ORDERBYDESCsys_updated_on";
    private int pageSize = 100;
    private boolean mockEnabled = false;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
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

    public String getQueryFilter() {
        return queryFilter;
    }

    public void setQueryFilter(String queryFilter) {
        this.queryFilter = queryFilter;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public boolean hasCredentials() {
        return (username != null && !username.isBlank() && password != null && !password.isBlank())
                || (bearerToken != null && !bearerToken.isBlank());
    }
}
