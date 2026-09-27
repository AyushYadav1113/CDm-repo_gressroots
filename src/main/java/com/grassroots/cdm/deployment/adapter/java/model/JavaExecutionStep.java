package com.grassroots.cdm.deployment.adapter.java.model;

/**
 * Conceptual execution steps for Java Keystore certificate deployment.
 */
public enum JavaExecutionStep {
    RECEIVE_CERTIFICATE_MATERIAL,
    UPDATE_KEYSTORE,
    SET_PERMISSIONS,
    UPDATE_APPLICATION_CONFIGURATION,
    RESTART_APPLICATION,
    VERIFY_LIVE_ENDPOINT,
    ROLLBACK_KEYSTORE
}
