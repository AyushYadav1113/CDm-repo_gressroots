package com.grassroots.cdm.deployment.adapter.java.exception;

/**
 * Thrown when pre-deployment validation of a Java deployment job or profile fails.
 */
public class JavaValidationException extends JavaDeploymentException {

    public JavaValidationException(String message) {
        super(message);
    }

    public JavaValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
