package com.grassroots.cdm.deployment.adapter.linux.model;

/**
 * Conceptual execution steps for Linux certificate deployment (Apache & Nginx).
 */
public enum LinuxExecutionStep {
    VALIDATE_PREREQUISITES,
    TRANSFER_CERTIFICATE,
    TRANSFER_PRIVATE_KEY,
    APPLY_RESTRICTIVE_PERMISSIONS,
    VALIDATE_CONFIGURATION,
    UPDATE_CERTIFICATE_CONFIGURATION,
    RELOAD_SERVICE,
    VERIFY_LIVE_ENDPOINT,
    ROLLBACK_CONFIGURATION
}
