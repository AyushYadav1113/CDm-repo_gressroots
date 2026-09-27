package com.grassroots.cdm.entity.enums;

/**
 * Category or target technology-specific type of certificate deployment execution.
 */
public enum DeploymentType {
    RENEWAL_REPLACEMENT,
    INITIAL_INSTALLATION,
    ROLLBACK,
    EMERGENCY_UPDATE,

    // Target technology-specific deployment types
    IIS,
    APACHE,
    NGINX,
    JAVA
}
