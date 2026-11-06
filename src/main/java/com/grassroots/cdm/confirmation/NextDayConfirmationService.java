package com.grassroots.cdm.confirmation;

import com.grassroots.cdm.confirmation.model.NextDayConfirmationResult;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for performing next-day certificate deployment confirmation.
 *
 * <p>On Day 2, ServiceNow Discovery independently scans the target environment.
 * This service validates that the new certificate is now reflected in CMDB,
 * and handles cases where the old certificate is still present, discovery data is
 * unavailable, or ambiguous data requires human review.</p>
 *
 * <p><strong>Safety invariants:</strong></p>
 * <ul>
 *   <li>A deployment is NEVER marked CONFIRMED if discovery data is absent or incomplete.</li>
 *   <li>Presence of the OLD certificate always results in CONFIRMATION_FAILED.</li>
 *   <li>A null ServiceNow response always results in DISCOVERY_UNAVAILABLE.</li>
 * </ul>
 */
public interface NextDayConfirmationService {

    /**
     * Performs a next-day confirmation check for a single deployment job.
     *
     * @param jobId         UUID of the deployment job to evaluate
     * @param correlationId correlation ID for audit/log tracing
     * @return a {@link NextDayConfirmationResult} never null
     */
    NextDayConfirmationResult confirmDeployment(UUID jobId, String correlationId);

    /**
     * Processes all deployment jobs that require next-day confirmation.
     *
     * <p>This is intended to be called by the scheduler. It identifies VERIFIED/COMPLETED jobs
     * whose confirmation has not yet been resolved and evaluates each one.</p>
     *
     * @param correlationId batch correlation ID for the current scheduler run
     * @return list of individual confirmation results, one per evaluated job
     */
    List<NextDayConfirmationResult> processAllPendingConfirmations(String correlationId);

    /**
     * Manually requests a next-day confirmation re-check for a specific job.
     *
     * <p>Used by operators after a DISCOVERY_UNAVAILABLE result to re-query once
     * discovery data becomes available without waiting for the next scheduled run.</p>
     *
     * @param jobId         UUID of the deployment job
     * @param correlationId correlation ID for the re-check
     * @return fresh {@link NextDayConfirmationResult}
     */
    NextDayConfirmationResult recheckConfirmation(UUID jobId, String correlationId);
}
