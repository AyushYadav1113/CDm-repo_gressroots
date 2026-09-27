package com.grassroots.cdm.deployment.adapter.linux.exception;

/**
 * Thrown when an automated rollback to the previous configuration backup fails.
 */
public class LinuxRollbackException extends LinuxDeploymentException {

    private final String backupConfigPath;
    private final String restoredThumbprint;

    public LinuxRollbackException(String message, String backupConfigPath, String restoredThumbprint) {
        super(message);
        this.backupConfigPath = backupConfigPath;
        this.restoredThumbprint = restoredThumbprint;
    }

    public LinuxRollbackException(String message, String backupConfigPath, String restoredThumbprint, Throwable cause) {
        super(message, cause);
        this.backupConfigPath = backupConfigPath;
        this.restoredThumbprint = restoredThumbprint;
    }

    public String getBackupConfigPath() {
        return backupConfigPath;
    }

    public String getRestoredThumbprint() {
        return restoredThumbprint;
    }
}
