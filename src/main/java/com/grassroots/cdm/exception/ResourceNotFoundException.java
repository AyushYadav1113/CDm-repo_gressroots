package com.grassroots.cdm.exception;

/**
 * Thrown when an expected entity or resource is not found.
 */
public class ResourceNotFoundException extends CdmException {

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(String.format("%s with identifier '%s' was not found", resourceName, identifier));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
