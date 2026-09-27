package com.grassroots.cdm.deployment.adapter.linux.exception;

/**
 * Thrown when pre-deployment validation for a Linux deployment fails (e.g. invalid server OS, expired cert, unassigned MID server).
 */
public class LinuxValidationException extends LinuxDeploymentException {

    public LinuxValidationException(String message) {
        super(message);
    }

    public LinuxValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
