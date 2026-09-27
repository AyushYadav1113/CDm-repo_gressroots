package com.grassroots.cdm.integration.sectigo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalized configuration properties for Sectigo Certificate Manager (SCM) REST API integration.
 * Sensitive fields (passwords, tokens) are loaded strictly from environment or secret stores and masked in logs.
 */
@Configuration
@ConfigurationProperties(prefix = "cdm.sectigo")
public class SectigoProperties {

    private String baseUrl = "https://cert-manager.com/api/v1";
    private String customerUri;
    private String loginName;
    private String password;
    private String apiToken;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private int maxRetries = 3;
    private long backoffMs = 1000;
    private int pageSize = 100;
    private String certificatesPath = "/certificates";
    private boolean mockEnabled = false;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCustomerUri() {
        return customerUri;
    }

    public void setCustomerUri(String customerUri) {
        this.customerUri = customerUri;
    }

    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
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

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getCertificatesPath() {
        return certificatesPath;
    }

    public void setCertificatesPath(String certificatesPath) {
        this.certificatesPath = certificatesPath;
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public boolean hasCredentials() {
        return (customerUri != null && !customerUri.isBlank()
                && loginName != null && !loginName.isBlank()
                && password != null && !password.isBlank())
                || (apiToken != null && !apiToken.isBlank());
    }

    @Override
    public String toString() {
        return "SectigoProperties{" +
                "baseUrl='" + baseUrl + '\'' +
                ", customerUri='" + (customerUri != null ? "***" : "null") + '\'' +
                ", loginName='" + (loginName != null ? "***" : "null") + '\'' +
                ", password='***'" +
                ", apiToken=" + (apiToken != null ? "'***'" : "null") +
                ", connectTimeoutMs=" + connectTimeoutMs +
                ", readTimeoutMs=" + readTimeoutMs +
                ", maxRetries=" + maxRetries +
                ", backoffMs=" + backoffMs +
                ", pageSize=" + pageSize +
                ", certificatesPath='" + certificatesPath + '\'' +
                ", mockEnabled=" + mockEnabled +
                '}';
    }
}
