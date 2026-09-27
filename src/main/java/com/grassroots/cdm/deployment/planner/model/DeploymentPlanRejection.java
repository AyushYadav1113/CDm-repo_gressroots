package com.grassroots.cdm.deployment.planner.model;

import java.util.UUID;

/**
 * Record representing an installation that was rejected during deployment planning.
 */
public record DeploymentPlanRejection(
        UUID installationId,
        String serverHostname,
        String reason,
        String errorType
) {}
