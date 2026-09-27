package com.grassroots.cdm.deployment.planner.exception;

import java.util.UUID;

/**
 * Thrown when target server cannot be found, is null, or is decommissioned.
 */
public class MissingServerException extends DeploymentPlanningException {

    private final UUID serverId;

    public MissingServerException(String message) {
        super(message);
        this.serverId = null;
    }

    public MissingServerException(UUID serverId, String message) {
        super(message);
        this.serverId = serverId;
    }

    public UUID getServerId() {
        return serverId;
    }
}
