package com.grassroots.cdm.integration.sectigo.exception;

/**
 * Thrown on 4xx client errors (other than 401/403/429), such as 400 Bad Request or 404 Not Found.
 * This exception is NON-RETRYABLE.
 */
public class SectigoClientException extends SectigoException {

    public SectigoClientException(String message, int statusCode) {
        super("Sectigo API client error: " + message, statusCode);
    }
}
