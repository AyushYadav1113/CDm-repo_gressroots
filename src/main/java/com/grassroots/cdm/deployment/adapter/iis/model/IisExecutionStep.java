package com.grassroots.cdm.deployment.adapter.iis.model;

/**
 * Conceptual execution steps for Windows / IIS SSL certificate deployment.
 */
public enum IisExecutionStep {
    VALIDATE_PREREQUISITES,
    IMPORT_CERTIFICATE,
    ENSURE_PRIVATE_KEY_PERMISSIONS,
    UPDATE_HTTPS_BINDING,
    VERIFY_BINDING,
    ROLLBACK_BINDING
}
