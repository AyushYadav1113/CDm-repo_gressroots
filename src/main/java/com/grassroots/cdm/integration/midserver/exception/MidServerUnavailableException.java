package com.grassroots.cdm.integration.midserver.exception;

/**
 * Thrown when the MID Server is unreachable, connection refused, or returns 503 Service Unavailable.
 */
public class MidServerUnavailableException extends MidServerException {

    private final String endpoint;

    public MidServerUnavailableException(String message, String endpoint) {
        super(message);
        this.endpoint = endpoint;
    }

    public MidServerUnavailableException(String message, String endpoint, Throwable cause) {
        super(message, cause);
        this.endpoint = endpoint;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
