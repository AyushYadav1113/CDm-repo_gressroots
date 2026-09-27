package com.grassroots.cdm.deployment.adapter.java.exception;

/**
 * Thrown when automated rollback to the previous Java keystore or configuration backup fails.
 */
public class JavaRollbackException extends JavaDeploymentException {

    private final String backupKeystorePath;
    private final String restoredThumbprint;

    public JavaRollbackException(String message, String backupKeystorePath, String restoredThumbprint) {
        super(message);
        this.backupKeystorePath = backupKeystorePath;
        this.restoredThumbprint = restoredThumbprint;
    }

    public JavaRollbackException(String message, String backupKeystorePath, String restoredThumbprint, Throwable cause) {
        super(message, cause);
        this.backupKeystorePath = backupKeystorePath;
        this.restoredThumbprint = restoredThumbprint;
    }

    public String getBackupKeystorePath() {
        return backupKeystorePath;
    }

    public String getRestoredThumbprint() {
        return restoredThumbprint;
    }
}
