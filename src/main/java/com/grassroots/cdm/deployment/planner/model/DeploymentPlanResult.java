package com.grassroots.cdm.deployment.planner.model;

import com.grassroots.cdm.entity.DeploymentJob;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Comprehensive result of a deployment planning operation.
 */
public record DeploymentPlanResult(
        UUID replacementId,
        UUID oldCertificateId,
        UUID newCertificateId,
        int totalInstallationsEvaluated,
        List<DeploymentJob> plannedJobs,
        List<DeploymentPlanRejection> rejections,
        Instant plannedAt
) {
    public boolean hasPlannedJobs() {
        return plannedJobs != null && !plannedJobs.isEmpty();
    }

    public boolean hasRejections() {
        return rejections != null && !rejections.isEmpty();
    }
}
