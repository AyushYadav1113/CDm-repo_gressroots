package com.grassroots.cdm.deployment.planner;

import com.grassroots.cdm.deployment.planner.model.DeploymentPlanResult;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateReplacement;
import com.grassroots.cdm.entity.DeploymentJob;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for deterministic deployment job planning.
 * Maps validated certificate replacements to their active server installations,
 * resolves technologies, verifies MID servers, generates idempotency keys,
 * and creates pending deployment orchestration records.
 */
public interface DeploymentPlanner {

    /**
     * Evaluates a certificate replacement and plans deployment jobs for all of its installations.
     *
     * @param replacement the certificate replacement pair
     * @return DeploymentPlanResult containing all planned jobs and any installation rejections
     */
    DeploymentPlanResult planDeployments(CertificateReplacement replacement);

    /**
     * Evaluates a certificate replacement by its UUID and plans deployment jobs.
     *
     * @param replacementId UUID of the CertificateReplacement
     * @return DeploymentPlanResult containing all planned jobs and any installation rejections
     */
    DeploymentPlanResult planDeployments(UUID replacementId);

    /**
     * Plans a single deployment job for a specific replacement and installation target.
     * Enforces all creation rules: match status, server existence, MID server suitability,
     * supported technology, duplicate detection, and completed deployment checks.
     *
     * @param replacement  the certificate replacement pair
     * @param installation the installation target
     * @return the created and persisted DeploymentJob
     */
    DeploymentJob planDeploymentForInstallation(CertificateReplacement replacement, CertificateInstallation installation);

    /**
     * Scans all AUTO_MATCHED / MANUALLY_CONFIRMED replacements and plans deployments for any that lack jobs.
     *
     * @return list of DeploymentPlanResult for each processed replacement
     */
    List<DeploymentPlanResult> planAllPendingDeployments();
}
