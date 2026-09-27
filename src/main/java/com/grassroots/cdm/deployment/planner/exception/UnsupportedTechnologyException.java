package com.grassroots.cdm.deployment.planner.exception;

/**
 * Thrown when target server technology is unsupported for automated certificate deployment.
 */
public class UnsupportedTechnologyException extends DeploymentPlanningException {

    private final String technology;

    public UnsupportedTechnologyException(String message) {
        super(message);
        this.technology = null;
    }

    public UnsupportedTechnologyException(String technology, String message) {
        super(message);
        this.technology = technology;
    }

    public String getTechnology() {
        return technology;
    }
}
