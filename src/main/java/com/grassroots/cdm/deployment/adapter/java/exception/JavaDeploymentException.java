package com.grassroots.cdm.deployment.adapter.java.exception;

/**
 * Base exception for all Java certificate deployment adapter errors.
 */
public class JavaDeploymentException extends RuntimeException {

    public JavaDeploymentException(String message) {
        super(message);
    }

    public JavaDeploymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
