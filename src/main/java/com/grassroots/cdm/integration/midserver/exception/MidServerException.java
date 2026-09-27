package com.grassroots.cdm.integration.midserver.exception;

/**
 * Base exception for all ServiceNow MID Server integration failures.
 */
public class MidServerException extends RuntimeException {

    public MidServerException(String message) {
        super(message);
    }

    public MidServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
