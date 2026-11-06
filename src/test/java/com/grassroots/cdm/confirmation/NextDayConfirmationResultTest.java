package com.grassroots.cdm.confirmation;

import com.grassroots.cdm.confirmation.model.NextDayConfirmationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NextDayConfirmationResultTest {

    private final UUID jobId = UUID.randomUUID();
    private final String correlationId = "corr-100";
    private final String host = "app-node-01.corp.grassroots.org";
    private final String expectedThumbprint = "AAAABBBBCCCCDDDDEEEEFFFF0000111122223333";
    private final String observedThumbprint = "AAAABBBBCCCCDDDDEEEEFFFF0000111122223333";
    private final String oldThumbprint = "9999888877776666555544443333222211110000";

    @Test
    @DisplayName("confirmed factory creates correct CONFIRMED result model")
    void confirmedFactory_populatesFieldsCorrectly() {
        NextDayConfirmationResult result = NextDayConfirmationResult.confirmed(
                jobId, correlationId, host, expectedThumbprint, observedThumbprint, "sys_ci_123");

        assertThat(result.getJobId()).isEqualTo(jobId);
        assertThat(result.getCorrelationId()).isEqualTo(correlationId);
        assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
        assertThat(result.getTargetHost()).isEqualTo(host);
        assertThat(result.getExpectedThumbprint()).isEqualTo(expectedThumbprint);
        assertThat(result.getObservedThumbprint()).isEqualTo(observedThumbprint);
        assertThat(result.getServiceNowCiSysId()).isEqualTo("sys_ci_123");
        assertThat(result.isConfirmed()).isTrue();
        assertThat(result.isFailed()).isFalse();
        assertThat(result.isUnavailable()).isFalse();
        assertThat(result.requiresManualReview()).isFalse();
        assertThat(result.getCheckedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(result.getMessage()).contains(host).contains(observedThumbprint);
    }

    @Test
    @DisplayName("failed factory creates correct CONFIRMATION_FAILED result model")
    void failedFactory_populatesFieldsCorrectly() {
        NextDayConfirmationResult result = NextDayConfirmationResult.failed(
                jobId, correlationId, host, expectedThumbprint, oldThumbprint, "Old certificate still active");

        assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMATION_FAILED);
        assertThat(result.isConfirmed()).isFalse();
        assertThat(result.isFailed()).isTrue();
        assertThat(result.getObservedThumbprint()).isEqualTo(oldThumbprint);
        assertThat(result.getMessage()).contains("Old certificate still active");
    }

    @Test
    @DisplayName("discoveryUnavailable factory creates DISCOVERY_UNAVAILABLE result model")
    void discoveryUnavailableFactory_populatesFieldsCorrectly() {
        NextDayConfirmationResult result = NextDayConfirmationResult.discoveryUnavailable(
                jobId, correlationId, host, "No CI records in ServiceNow");

        assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.DISCOVERY_UNAVAILABLE);
        assertThat(result.isUnavailable()).isTrue();
        assertThat(result.isConfirmed()).isFalse();
        assertThat(result.getMessage()).contains("No CI records in ServiceNow");
    }

    @Test
    @DisplayName("manualReview factory creates MANUAL_REVIEW result model")
    void manualReviewFactory_populatesFieldsCorrectly() {
        NextDayConfirmationResult result = NextDayConfirmationResult.manualReview(
                jobId, correlationId, host, "Conflict between multiple discovery sources");

        assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.MANUAL_REVIEW);
        assertThat(result.requiresManualReview()).isTrue();
        assertThat(result.getMessage()).contains("Conflict between multiple discovery sources");
    }

    @Test
    @DisplayName("pendingConfirmation factory creates PENDING_CONFIRMATION result model")
    void pendingConfirmationFactory_populatesFieldsCorrectly() {
        NextDayConfirmationResult result = NextDayConfirmationResult.pendingConfirmation(
                jobId, correlationId, host);

        assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.PENDING_CONFIRMATION);
        assertThat(result.isConfirmed()).isFalse();
        assertThat(result.getMessage()).contains("pending");
    }

    @Test
    @DisplayName("toString includes essential diagnostics")
    void toString_containsDiagnosticFields() {
        NextDayConfirmationResult result = NextDayConfirmationResult.confirmed(
                jobId, correlationId, host, expectedThumbprint, observedThumbprint, "sys_ci_123");

        String str = result.toString();
        assertThat(str)
                .contains(jobId.toString())
                .contains(host)
                .contains("CONFIRMED");
    }
}
