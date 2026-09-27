package com.grassroots.cdm.integration.sectigo.exception;

/**
 * Thrown on 5xx server errors (e.g. 500 Internal Server Error, 502 Bad Gateway, 503 Service Unavailable).
 * This exception is RETRYABLE with exponential backoff.
 */
public class SectigoServerException extends SectigoException {

    public SectigoServerException(String message, int statusCode) {
        super("Sectigo API server error: " + message, statusCode);
    }

    public SectigoServerException(String message, int statusCode, Throwable cause) {
        super("Sectigo API server error: " + message, statusCode, cause);
    }
}
