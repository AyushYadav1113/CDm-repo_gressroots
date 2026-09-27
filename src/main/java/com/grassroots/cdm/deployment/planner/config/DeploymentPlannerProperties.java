package com.grassroots.cdm.deployment.planner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Externalized configuration properties governing Deployment Planner rules and thresholds.
 */
@Component
@ConfigurationProperties(prefix = "cdm.deployment.planner")
public class DeploymentPlannerProperties {

    private boolean enabled = true;
    private int defaultMaxRetries = 3;
    private int criticalThresholdDays = 7;
    private int highThresholdDays = 15;
    private boolean allowManualReviewOverride = false;
    private int defaultPort = 443;
    private boolean requireActiveMidServer = true;

    private Set<String> supportedTechnologies = Set.of(
            "IIS",
            "APACHE",
            "NGINX",
            "JAVA_KEYSTORE",
            "TOMCAT",
            "WEBLOGIC",
            "WEBSPHERE"
    );

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultMaxRetries() {
        return defaultMaxRetries;
    }

    public void setDefaultMaxRetries(int defaultMaxRetries) {
        this.defaultMaxRetries = defaultMaxRetries;
    }

    public int getCriticalThresholdDays() {
        return criticalThresholdDays;
    }

    public void setCriticalThresholdDays(int criticalThresholdDays) {
        this.criticalThresholdDays = criticalThresholdDays;
    }

    public int getHighThresholdDays() {
        return highThresholdDays;
    }

    public void setHighThresholdDays(int highThresholdDays) {
        this.highThresholdDays = highThresholdDays;
    }

    public boolean isAllowManualReviewOverride() {
        return allowManualReviewOverride;
    }

    public void setAllowManualReviewOverride(boolean allowManualReviewOverride) {
        this.allowManualReviewOverride = allowManualReviewOverride;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public void setDefaultPort(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public boolean isRequireActiveMidServer() {
        return requireActiveMidServer;
    }

    public void setRequireActiveMidServer(boolean requireActiveMidServer) {
        this.requireActiveMidServer = requireActiveMidServer;
    }

    public Set<String> getSupportedTechnologies() {
        return supportedTechnologies;
    }

    public void setSupportedTechnologies(Set<String> supportedTechnologies) {
        this.supportedTechnologies = supportedTechnologies;
    }
}
