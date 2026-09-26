package com.grassroots.cdm.verification;

import java.util.UUID;

/**
 * Interface contract for next-day discovery verification.
 * Confirms that ServiceNow Discovery has independently scanned and reconciled
 * the newly deployed certificate on target CIs during scheduled discovery schedules.
 */
public interface NextDayDiscoveryVerifier {

    /**
     * Reconciles ServiceNow discovery status for a completed deployment job.
     *
     * @param jobId UUID of the completed DeploymentJob
     * @return true if ServiceNow CMDB reflects the newly deployed certificate
     */
    boolean verifyDiscoveryReconciliation(UUID jobId);
}
