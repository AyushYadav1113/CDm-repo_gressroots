package com.grassroots.cdm.deployment.planner.exception;

/**
 * Thrown when a target server lacks an assigned MID Server or its assigned MID Server
 * is down, paused, or non-operational.
 */
public class MissingMidServerException extends DeploymentPlanningException {

    public MissingMidServerException(String message) {
        super(message);
    }
}
