package com.grassroots.cdm.deployment.planner.exception;

/**
 * Thrown when attempting to plan a deployment for a certificate replacement that has no match
 * or has been rejected/superseded.
 */
public class NoMatchException extends DeploymentPlanningException {

    public NoMatchException(String message) {
        super(message);
    }
}
