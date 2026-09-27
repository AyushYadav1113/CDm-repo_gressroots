package com.grassroots.cdm.integration.midserver.exception;

/**
 * Thrown when an HTTP connection or read timeout occurs communicating with the MID Server.
 */
public class MidServerTimeoutException extends MidServerException {

    private final long timeoutMs;

    public MidServerTimeoutException(String message, long timeoutMs) {
        super(message);
        this.timeoutMs = timeoutMs;
    }

    public MidServerTimeoutException(String message, long timeoutMs, Throwable cause) {
        super(message, cause);
        this.timeoutMs = timeoutMs;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }
}
