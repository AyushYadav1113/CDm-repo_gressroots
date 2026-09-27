package com.grassroots.cdm.integration.midserver.exception;

/**
 * Thrown when the MID Server detects a duplicate request with an identical idempotency key.
 */
public class MidServerDuplicateRequestException extends MidServerException {

    private final String idempotencyKey;
    private final String existingTaskId;

    public MidServerDuplicateRequestException(String message, String idempotencyKey, String existingTaskId) {
        super(message);
        this.idempotencyKey = idempotencyKey;
        this.existingTaskId = existingTaskId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getExistingTaskId() {
        return existingTaskId;
    }
}
