package com.grassroots.cdm.integration.sectigo.exception;

/**
 * Thrown when Sectigo rejects credentials (HTTP 401 Unauthorized or HTTP 403 Forbidden).
 * This exception is NON-RETRYABLE.
 */
public class SectigoAuthenticationException extends SectigoException {

    public SectigoAuthenticationException(String message, int statusCode) {
        super("Authentication failed with Sectigo SCM API: " + message, statusCode);
    }
}
