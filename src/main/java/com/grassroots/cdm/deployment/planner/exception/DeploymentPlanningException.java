package com.grassroots.cdm.deployment.planner.exception;

/**
 * Base exception for deployment planning failures.
 */
public class DeploymentPlanningException extends RuntimeException {

    public DeploymentPlanningException(String message) {
        super(message);
    }

    public DeploymentPlanningException(String message, Throwable cause) {
        super(message, cause);
    }
}
