package com.grassroots.cdm.confirmation;

/**
 * Status values for next-day certificate deployment confirmation checks.
 *
 * <p>These statuses reflect the result of querying ServiceNow Discovery data
 * the day after a certificate was deployed, to confirm CMDB reflects the new certificate.</p>
 */
public enum ConfirmationStatus {

    /**
     * Confirmation check is awaited — the daily job has not yet processed this deployment.
     */
    PENDING_CONFIRMATION,

    /**
     * ServiceNow CMDB now reflects the new certificate on the target CI.
     * Deployment is fully confirmed end-to-end.
     */
    CONFIRMED,

    /**
     * ServiceNow Discovery data shows the OLD certificate still present on the target CI.
     * The deployment may have failed silently or discovery has not yet picked up the change.
     */
    CONFIRMATION_FAILED,

    /**
     * ServiceNow Discovery data is absent or incomplete for the target host.
     * It is not safe to mark the deployment as confirmed or failed — status is unknown.
     */
    DISCOVERY_UNAVAILABLE,

    /**
     * Confirmation could not be determined automatically and requires a human operator.
     * Typically set after repeated DISCOVERY_UNAVAILABLE results or certificate mismatch.
     */
    MANUAL_REVIEW
}
