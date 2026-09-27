package com.grassroots.cdm.workflow.scheduler;

import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.discovery.DiscoveryService;
import com.grassroots.cdm.dto.DiscoveryResultDto;
import com.grassroots.cdm.metrics.CdmMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DiscoveryScheduler.
 */
@ExtendWith(MockitoExtension.class)
class DiscoverySchedulerTest {

    @Mock DiscoveryService discoveryService;
    @Mock AuditService auditService;
    @Mock CdmMetricsService metricsService;

    private DiscoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DiscoveryScheduler(discoveryService, auditService, metricsService);
        doNothing().when(metricsService).recordWorkflowDuration(anyString(), any(Duration.class));
        doNothing().when(auditService).recordAudit(any());
    }

    @Test
    void runScheduledDiscovery_invokes_discoverCertificates_and_records_metrics() {
        DiscoveryResultDto result = new DiscoveryResultDto("corr-1", 25, 20, 3, 2, 1000L, List.of());
        when(discoveryService.discoverCertificates(anyString())).thenReturn(result);

        scheduler.runScheduledDiscovery();

        verify(discoveryService, times(1)).discoverCertificates(anyString());
        verify(metricsService, times(1)).recordCertificatesDiscovered(eq("SERVICENOW"), eq(25));
        verify(auditService, times(1)).recordAudit(any());
    }

    @Test
    void runScheduledDiscovery_records_failure_audit_when_discovery_throws() {
        when(discoveryService.discoverCertificates(anyString()))
                .thenThrow(new RuntimeException("ServiceNow unavailable"));

        assertThatNoException().isThrownBy(() -> scheduler.runScheduledDiscovery());

        verify(auditService, times(1)).recordAudit(any());
        verify(metricsService, times(1)).recordWorkflowDuration(eq("discovery"), any(Duration.class));
    }

    @Test
    void runScheduledDiscovery_still_records_duration_on_failure() {
        when(discoveryService.discoverCertificates(anyString()))
                .thenThrow(new RuntimeException("Timeout"));

        scheduler.runScheduledDiscovery();

        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(metricsService, times(1)).recordWorkflowDuration(eq("discovery"), durationCaptor.capture());
        assertThat(durationCaptor.getValue()).isGreaterThanOrEqualTo(Duration.ZERO);
    }
}
