package com.grassroots.cdm.integration.midserver.exception;

/**
 * Thrown when the MID Server execution task terminates with a non-zero exit code or reported failure state.
 */
public class MidServerJobFailedException extends MidServerException {

    private final String taskId;
    private final int exitCode;
    private final String errorSummary;

    public MidServerJobFailedException(String message, String taskId, int exitCode, String errorSummary) {
        super(message);
        this.taskId = taskId;
        this.exitCode = exitCode;
        this.errorSummary = errorSummary;
    }

    public String getTaskId() {
        return taskId;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getErrorSummary() {
        return errorSummary;
    }
}
