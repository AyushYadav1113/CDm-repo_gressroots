package com.grassroots.cdm.integration.sectigo.exception;

/**
 * Thrown when HTTP connect or socket read times out when calling Sectigo SCM API.
 * This exception is RETRYABLE with exponential backoff.
 */
public class SectigoTimeoutException extends SectigoException {

    public SectigoTimeoutException(String message, Throwable cause) {
        super("Sectigo API connection or socket timed out: " + message, cause);
    }
}
