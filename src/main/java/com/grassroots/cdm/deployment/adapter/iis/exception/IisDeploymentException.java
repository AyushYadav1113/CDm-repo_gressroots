package com.grassroots.cdm.deployment.adapter.iis.exception;

/**
 * Base exception for all IIS / Windows deployment adapter errors.
 */
public class IisDeploymentException extends RuntimeException {

    public IisDeploymentException(String message) {
        super(message);
    }

    public IisDeploymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
