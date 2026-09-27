package com.grassroots.cdm.deployment.adapter.linux.exception;

/**
 * Base exception for all Linux (Apache & Nginx) deployment adapter errors.
 */
public class LinuxDeploymentException extends RuntimeException {

    public LinuxDeploymentException(String message) {
        super(message);
    }

    public LinuxDeploymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
