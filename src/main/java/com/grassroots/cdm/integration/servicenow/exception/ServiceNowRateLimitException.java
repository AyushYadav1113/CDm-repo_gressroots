package com.grassroots.cdm.integration.servicenow.exception;

/**
 * Thrown when ServiceNow returns HTTP 429 Too Many Requests (rate limiting).
 */
public class ServiceNowRateLimitException extends ServiceNowException {

    private final Long retryAfterSeconds;

    public ServiceNowRateLimitException(String message, Long retryAfterSeconds) {
        super(message, 429);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
