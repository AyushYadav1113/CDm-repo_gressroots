package com.grassroots.cdm.integration.midserver.exception;

/**
 * Thrown when an asynchronous execution task running on the MID Server exceeds its maximum execution window.
 */
public class MidServerExecutionTimeoutException extends MidServerException {

    private final String taskId;
    private final int timeoutSeconds;

    public MidServerExecutionTimeoutException(String message, String taskId, int timeoutSeconds) {
        super(message);
        this.taskId = taskId;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getTaskId() {
        return taskId;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
