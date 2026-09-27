package com.grassroots.cdm.integration.sectigo.exception;

/**
 * Thrown when Sectigo response payload is malformed or cannot be parsed into expected DTO.
 * This exception is NON-RETRYABLE.
 */
public class SectigoParseException extends SectigoException {

    public SectigoParseException(String message, Throwable cause) {
        super("Failed parsing Sectigo response: " + message, cause);
    }
}
