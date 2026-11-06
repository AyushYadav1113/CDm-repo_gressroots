package com.grassroots.cdm.workflow.scheduler;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.confirmation.NextDayConfirmationService;
import com.grassroots.cdm.confirmation.model.NextDayConfirmationResult;
import com.grassroots.cdm.metrics.CdmMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NextDayConfirmationSchedulerTest {

    @Mock
    private NextDayConfirmationService confirmationService;

    @Mock
    private AuditService auditService;

    @Mock
    private CdmMetricsService metricsService;

    private NextDayConfirmationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NextDayConfirmationScheduler(confirmationService, auditService, metricsService);
    }

    @Test
    @DisplayName("runScheduledNextDayConfirmation handles empty pending list smoothly")
    void runScheduledNextDayConfirmation_emptyPending_recordsMetricsAndAudit() {
        when(confirmationService.processAllPendingConfirmations(anyString()))
                .thenReturn(Collections.emptyList());

        scheduler.runScheduledNextDayConfirmation();

        verify(metricsService).recordWorkflowDuration(eq("next_day_confirmation"), any(Duration.class));
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).recordAudit(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.DISCOVERY_RECONCILED);
        assertThat(event.outcome()).isEqualTo("SUCCESS");
        assertThat(event.details()).contains("evaluated=0");
    }

    @Test
    @DisplayName("runScheduledNextDayConfirmation tallies confirmed, failed, and unavailable outcomes")
    void runScheduledNextDayConfirmation_mixedOutcomes_recordsMetricsAndAudit() {
        UUID job1 = UUID.randomUUID();
        UUID job2 = UUID.randomUUID();
        UUID job3 = UUID.randomUUID();

        NextDayConfirmationResult r1 = NextDayConfirmationResult.confirmed(job1, "c1", "host1", "thumb1", "thumb1", "sys1");
        NextDayConfirmationResult r2 = NextDayConfirmationResult.failed(job2, "c2", "host2", "thumb2", "oldThumb", "Old cert present");
        NextDayConfirmationResult r3 = NextDayConfirmationResult.discoveryUnavailable(job3, "c3", "host3", "ServiceNow timed out");

        when(confirmationService.processAllPendingConfirmations(anyString()))
                .thenReturn(List.of(r1, r2, r3));

        scheduler.runScheduledNextDayConfirmation();

        verify(metricsService).recordCertificatesMatched("NEXT_DAY_CONFIRMED", 1);
        verify(metricsService).recordDeploymentFailed("CONFIRMATION_FAILED", "NEXT_DAY");
        verify(metricsService).recordVerificationFailure("DISCOVERY_UNAVAILABLE");
        verify(metricsService).recordWorkflowDuration(eq("next_day_confirmation"), any(Duration.class));

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).recordAudit(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertThat(event.outcome()).isEqualTo("PARTIAL");
        assertThat(event.details()).contains("evaluated=3 confirmed=1 failed=1 unavailable=1");
    }

    @Test
    @DisplayName("runScheduledNextDayConfirmation catches exceptions, logs audit failure, and does not re-throw")
    void runScheduledNextDayConfirmation_exceptionInService_logsFailureAudit() {
        when(confirmationService.processAllPendingConfirmations(anyString()))
                .thenThrow(new RuntimeException("Critical database failure"));

        scheduler.runScheduledNextDayConfirmation();

        verify(metricsService).recordWorkflowDuration(eq("next_day_confirmation"), any(Duration.class));
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).recordAudit(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertThat(event.outcome()).isEqualTo("FAILURE");
        assertThat(event.details()).contains("Critical database failure");
    }
}
