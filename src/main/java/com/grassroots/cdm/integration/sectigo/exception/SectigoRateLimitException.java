package com.grassroots.cdm.integration.sectigo.exception;

/**
 * Thrown when Sectigo responds with HTTP 429 Too Many Requests.
 * This exception is RETRYABLE, respecting the Retry-After header if provided.
 */
public class SectigoRateLimitException extends SectigoException {

    private final Long retryAfterSeconds;

    public SectigoRateLimitException(String message, Long retryAfterSeconds) {
        super(String.format("Rate limit exceeded by Sectigo API: %s (retryAfter: %s)", message, retryAfterSeconds), 429);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
