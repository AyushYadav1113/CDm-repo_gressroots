package com.grassroots.cdm.deployment.planner.exception;

import java.util.UUID;

/**
 * Thrown when an automated deployment job is requested for a certificate replacement
 * that is ambiguous or in REVIEW_REQUIRED / PENDING_REVIEW status.
 */
public class AmbiguousMatchException extends DeploymentPlanningException {

    private final UUID replacementId;

    public AmbiguousMatchException(String message) {
        super(message);
        this.replacementId = null;
    }

    public AmbiguousMatchException(UUID replacementId, String message) {
        super(message);
        this.replacementId = replacementId;
    }

    public UUID getReplacementId() {
        return replacementId;
    }
}
