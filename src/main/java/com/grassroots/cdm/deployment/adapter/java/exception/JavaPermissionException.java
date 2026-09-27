package com.grassroots.cdm.deployment.adapter.java.exception;

/**
 * Thrown when restrictive file permissions (e.g. mode 0600/0640) cannot be applied to the keystore file,
 * or when insecure permissions are requested.
 */
public class JavaPermissionException extends JavaDeploymentException {

    private final String keystorePath;
    private final String attemptedMode;
    private final int exitCode;

    public JavaPermissionException(String message, String keystorePath, String attemptedMode, int exitCode) {
        super(message);
        this.keystorePath = keystorePath;
        this.attemptedMode = attemptedMode;
        this.exitCode = exitCode;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public String getAttemptedMode() {
        return attemptedMode;
    }

    public int getExitCode() {
        return exitCode;
    }
}
