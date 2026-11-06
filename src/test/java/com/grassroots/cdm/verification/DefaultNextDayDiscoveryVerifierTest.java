package com.grassroots.cdm.verification;

import com.grassroots.cdm.confirmation.NextDayConfirmationService;
import com.grassroots.cdm.confirmation.model.NextDayConfirmationResult;
import com.grassroots.cdm.verification.impl.DefaultNextDayDiscoveryVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultNextDayDiscoveryVerifierTest {

    @Mock
    private NextDayConfirmationService confirmationService;

    private DefaultNextDayDiscoveryVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new DefaultNextDayDiscoveryVerifier(confirmationService);
    }

    @Test
    @DisplayName("verifyDiscoveryReconciliation returns false on null jobId")
    void verifyDiscoveryReconciliation_nullJobId_returnsFalse() {
        boolean result = verifier.verifyDiscoveryReconciliation(null);

        assertThat(result).isFalse();
        verify(confirmationService, never()).confirmDeployment(any(), any());
    }

    @Test
    @DisplayName("verifyDiscoveryReconciliation returns true when confirmation status is CONFIRMED")
    void verifyDiscoveryReconciliation_confirmedStatus_returnsTrue() {
        UUID jobId = UUID.randomUUID();
        NextDayConfirmationResult confirmationResult = NextDayConfirmationResult.confirmed(
                jobId, "corr-1", "host-1", "thumb1", "thumb1", "sys1");

        when(confirmationService.confirmDeployment(eq(jobId), any())).thenReturn(confirmationResult);

        boolean result = verifier.verifyDiscoveryReconciliation(jobId);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verifyDiscoveryReconciliation returns false when confirmation status is CONFIRMATION_FAILED")
    void verifyDiscoveryReconciliation_failedStatus_returnsFalse() {
        UUID jobId = UUID.randomUUID();
        NextDayConfirmationResult confirmationResult = NextDayConfirmationResult.failed(
                jobId, "corr-1", "host-1", "thumb1", "oldThumb", "Old cert present");

        when(confirmationService.confirmDeployment(eq(jobId), any())).thenReturn(confirmationResult);

        boolean result = verifier.verifyDiscoveryReconciliation(jobId);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verifyDiscoveryReconciliation returns false when confirmation status is DISCOVERY_UNAVAILABLE")
    void verifyDiscoveryReconciliation_unavailableStatus_returnsFalse() {
        UUID jobId = UUID.randomUUID();
        NextDayConfirmationResult confirmationResult = NextDayConfirmationResult.discoveryUnavailable(
                jobId, "corr-1", "host-1", "CMDB down");

        when(confirmationService.confirmDeployment(eq(jobId), any())).thenReturn(confirmationResult);

        boolean result = verifier.verifyDiscoveryReconciliation(jobId);

        assertThat(result).isFalse();
    }
}
