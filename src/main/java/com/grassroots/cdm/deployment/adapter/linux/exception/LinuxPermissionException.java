package com.grassroots.cdm.deployment.adapter.linux.exception;

/**
 * Thrown when restrictive POSIX file permissions (e.g. 0600/0640) or ownership
 * cannot be applied to the certificate or private key files on Linux,
 * or when insecure permissions are requested.
 */
public class LinuxPermissionException extends LinuxDeploymentException {

    private final String targetPath;
    private final String attemptedMode;
    private final int exitCode;

    public LinuxPermissionException(String message, String targetPath, String attemptedMode, int exitCode) {
        super(message);
        this.targetPath = targetPath;
        this.attemptedMode = attemptedMode;
        this.exitCode = exitCode;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public String getAttemptedMode() {
        return attemptedMode;
    }

    public int getExitCode() {
        return exitCode;
    }
}
