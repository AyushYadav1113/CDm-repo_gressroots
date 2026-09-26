package com.grassroots.cdm.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for CDM security and internal orchestrator authentication.
 */
@Configuration
@ConfigurationProperties(prefix = "cdm.security")
public class CdmSecurityProperties {

    private String adminUsername = "cdm_admin";
    private String adminPassword = "admin_dev_password_change_me";

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }
}
