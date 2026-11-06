package com.grassroots.cdm.verification.impl;

import com.grassroots.cdm.confirmation.NextDayConfirmationService;
import com.grassroots.cdm.confirmation.model.NextDayConfirmationResult;
import com.grassroots.cdm.verification.NextDayDiscoveryVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Adapter that bridges the legacy {@link NextDayDiscoveryVerifier} boolean contract
 * to the full {@link NextDayConfirmationService}.
 *
 * <p>Returns {@code true} only when the confirmation status is
 * {@code CONFIRMED} — never on DISCOVERY_UNAVAILABLE or ambiguous data.</p>
 */
@Service
public class DefaultNextDayDiscoveryVerifier implements NextDayDiscoveryVerifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultNextDayDiscoveryVerifier.class);

    private final NextDayConfirmationService confirmationService;

    public DefaultNextDayDiscoveryVerifier(NextDayConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    @Override
    public boolean verifyDiscoveryReconciliation(UUID jobId) {
        if (jobId == null) {
            log.warn("verifyDiscoveryReconciliation called with null jobId");
            return false;
        }

        String correlationId = UUID.randomUUID().toString();
        NextDayConfirmationResult result = confirmationService.confirmDeployment(jobId, correlationId);

        log.debug("Discovery reconciliation for job {} → status={} message={}",
                jobId, result.getStatus(), result.getMessage());

        // SAFETY: only return true on an explicit CONFIRMED verdict
        return result.isConfirmed();
    }
}
