package com.grassroots.cdm.integration.sectigo.exception;

import com.grassroots.cdm.exception.IntegrationException;

/**
 * Base exception for all Sectigo Certificate Manager (SCM) integration faults.
 */
public class SectigoException extends IntegrationException {

    private final Integer statusCode;

    public SectigoException(String message) {
        super("SECTIGO", message);
        this.statusCode = null;
    }

    public SectigoException(String message, Throwable cause) {
        super("SECTIGO", message, cause);
        this.statusCode = null;
    }

    public SectigoException(String message, int statusCode) {
        super("SECTIGO", String.format("%s (HTTP %d)", message, statusCode));
        this.statusCode = statusCode;
    }

    public SectigoException(String message, int statusCode, Throwable cause) {
        super("SECTIGO", String.format("%s (HTTP %d)", message, statusCode), cause);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
