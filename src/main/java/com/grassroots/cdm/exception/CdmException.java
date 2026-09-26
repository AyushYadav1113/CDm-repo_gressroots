package com.grassroots.cdm.exception;

/**
 * Root unchecked exception for all CDM application-specific errors.
 */
public class CdmException extends RuntimeException {

    public CdmException(String message) {
        super(message);
    }

    public CdmException(String message, Throwable cause) {
        super(message, cause);
    }
}
