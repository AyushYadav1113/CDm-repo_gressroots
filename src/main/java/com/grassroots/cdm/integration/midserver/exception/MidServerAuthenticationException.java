package com.grassroots.cdm.integration.midserver.exception;

/**
 * Thrown when MID Server rejects CDM authentication credentials (HTTP 401 or 403).
 */
public class MidServerAuthenticationException extends MidServerException {

    private final int statusCode;

    public MidServerAuthenticationException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
