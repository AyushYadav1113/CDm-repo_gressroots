package com.grassroots.cdm.deployment.adapter.iis.exception;

/**
 * Thrown when target server, certificate, or binding configuration fails pre-deployment validation.
 */
public class IisValidationException extends IisDeploymentException {

    public IisValidationException(String message) {
        super(message);
    }
}
