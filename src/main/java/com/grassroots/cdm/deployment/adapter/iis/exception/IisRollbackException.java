package com.grassroots.cdm.deployment.adapter.iis.exception;

/**
 * Thrown when an automated rollback to the previous certificate binding fails.
 */
public class IisRollbackException extends IisDeploymentException {

    private final String rollbackThumbprint;

    public IisRollbackException(String message, String rollbackThumbprint) {
        super(message);
        this.rollbackThumbprint = rollbackThumbprint;
    }

    public IisRollbackException(String message, String rollbackThumbprint, Throwable cause) {
        super(message, cause);
        this.rollbackThumbprint = rollbackThumbprint;
    }

    public String getRollbackThumbprint() {
        return rollbackThumbprint;
    }
}
