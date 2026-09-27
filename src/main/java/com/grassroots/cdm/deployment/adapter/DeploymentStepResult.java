package com.grassroots.cdm.deployment.adapter;

/**
 * Result of an individual conceptual step executed by a technology deployment adapter.
 */
public record DeploymentStepResult(
        String stepName,
        Status status,
        long durationMs,
        String details,
        Integer errorCode,
        String errorSummary
) {
    public enum Status {
        SUCCESS,
        FAILED,
        SKIPPED,
        ROLLED_BACK
    }

    public static DeploymentStepResult success(String stepName, long durationMs, String details) {
        return new DeploymentStepResult(stepName, Status.SUCCESS, durationMs, details, 0, null);
    }

    public static DeploymentStepResult failure(String stepName, long durationMs, String details, int errorCode, String errorSummary) {
        return new DeploymentStepResult(stepName, Status.FAILED, durationMs, details, errorCode, errorSummary);
    }

    public static DeploymentStepResult skipped(String stepName, String details) {
        return new DeploymentStepResult(stepName, Status.SKIPPED, 0L, details, null, null);
    }

    public static DeploymentStepResult rolledBack(String stepName, long durationMs, String details) {
        return new DeploymentStepResult(stepName, Status.ROLLED_BACK, durationMs, details, 0, null);
    }
}
