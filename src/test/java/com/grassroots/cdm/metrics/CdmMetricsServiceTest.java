package com.grassroots.cdm.metrics;

import com.grassroots.cdm.metrics.impl.DefaultCdmMetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DefaultCdmMetricsService.
 */
class CdmMetricsServiceTest {

    private MeterRegistry registry;
    private DefaultCdmMetricsService metricsService;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metricsService = new DefaultCdmMetricsService(registry);
    }

    @Test
    void recordCertificatesDiscovered_increments_counter_with_source_tag() {
        metricsService.recordCertificatesDiscovered("SERVICENOW", 42);

        Counter counter = registry.find(DefaultCdmMetricsService.METRIC_CERTIFICATES_DISCOVERED)
                .tag("source", "SERVICENOW")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(42.0);
    }

    @Test
    void recordCertificatesDiscovered_ignores_zero_count() {
        metricsService.recordCertificatesDiscovered("SERVICENOW", 0);

        Counter counter = registry.find(DefaultCdmMetricsService.METRIC_CERTIFICATES_DISCOVERED).counter();
        assertThat(counter).isNull();
    }

    @Test
    void recordCertificatesMatched_increments_per_decision() {
        metricsService.recordCertificatesMatched("AUTO_MATCH", 10);
        metricsService.recordCertificatesMatched("NO_MATCH", 3);

        double autoMatch = registry.find(DefaultCdmMetricsService.METRIC_CERTIFICATES_MATCHED)
                .tag("decision", "AUTO_MATCH").counter().count();
        double noMatch = registry.find(DefaultCdmMetricsService.METRIC_CERTIFICATES_MATCHED)
                .tag("decision", "NO_MATCH").counter().count();

        assertThat(autoMatch).isEqualTo(10.0);
        assertThat(noMatch).isEqualTo(3.0);
    }

    @Test
    void recordDeploymentJobCreated_increments_per_technology() {
        metricsService.recordDeploymentJobCreated("IIS");
        metricsService.recordDeploymentJobCreated("IIS");
        metricsService.recordDeploymentJobCreated("NGINX");

        double iisCount = registry.find(DefaultCdmMetricsService.METRIC_DEPLOYMENT_JOBS_CREATED)
                .tag("technology", "IIS").counter().count();
        double nginxCount = registry.find(DefaultCdmMetricsService.METRIC_DEPLOYMENT_JOBS_CREATED)
                .tag("technology", "NGINX").counter().count();

        assertThat(iisCount).isEqualTo(2.0);
        assertThat(nginxCount).isEqualTo(1.0);
    }

    @Test
    void recordDeploymentSuccessful_increments_success_counter() {
        metricsService.recordDeploymentSuccessful("APACHE");

        double count = registry.find(DefaultCdmMetricsService.METRIC_DEPLOYMENTS_SUCCESSFUL)
                .tag("technology", "APACHE").counter().count();

        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void recordDeploymentFailed_increments_failure_counter_with_failure_type() {
        metricsService.recordDeploymentFailed("CREDENTIAL_FAILED", "IIS");

        double count = registry.find(DefaultCdmMetricsService.METRIC_DEPLOYMENTS_FAILED)
                .tag("failure_type", "CREDENTIAL_FAILED")
                .tag("technology", "IIS")
                .counter().count();

        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void recordVerificationFailure_increments_counter_with_reason() {
        metricsService.recordVerificationFailure("CERTIFICATE_MISMATCH");

        double count = registry.find(DefaultCdmMetricsService.METRIC_VERIFICATION_FAILURES)
                .tag("reason", "CERTIFICATE_MISMATCH").counter().count();

        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void recordMidServerFailure_increments_counter_with_target() {
        metricsService.recordMidServerFailure("IIS");

        double count = registry.find(DefaultCdmMetricsService.METRIC_MID_SERVER_FAILURES)
                .tag("target_type", "IIS").counter().count();

        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void recordRetryAttempt_increments_retry_counter() {
        metricsService.recordRetryAttempt(2);

        double count = registry.find(DefaultCdmMetricsService.METRIC_RETRY_COUNTS)
                .tag("attempt", "2").counter().count();

        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void recordWorkflowDuration_publishes_timer() {
        metricsService.recordWorkflowDuration("discovery", Duration.ofMillis(500));

        Timer timer = registry.find(DefaultCdmMetricsService.METRIC_WORKFLOW_DURATION)
                .tag("workflow", "discovery").timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    void recordWorkflowDuration_ignores_null_or_negative_duration() {
        metricsService.recordWorkflowDuration("discovery", null);
        metricsService.recordWorkflowDuration("discovery", Duration.ofMillis(-1));

        // Timer should not have been registered
        Timer timer = registry.find(DefaultCdmMetricsService.METRIC_WORKFLOW_DURATION)
                .tag("workflow", "discovery").timer();
        if (timer != null) {
            assertThat(timer.count()).isEqualTo(0L);
        }
    }

    @Test
    void recordTimedWorkflow_with_runnable_records_duration() {
        metricsService.recordTimedWorkflow("planning", () -> {
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        });

        Timer timer = registry.find(DefaultCdmMetricsService.METRIC_WORKFLOW_DURATION)
                .tag("workflow", "planning").timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }

    @Test
    void recordTimedWorkflow_with_supplier_returns_value() {
        String result = metricsService.recordTimedWorkflow("matching", () -> "test-result");

        assertThat(result).isEqualTo("test-result");
        Timer timer = registry.find(DefaultCdmMetricsService.METRIC_WORKFLOW_DURATION)
                .tag("workflow", "matching").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    void null_source_defaults_to_UNKNOWN_tag() {
        metricsService.recordCertificatesDiscovered(null, 5);

        Counter counter = registry.find(DefaultCdmMetricsService.METRIC_CERTIFICATES_DISCOVERED)
                .tag("source", "UNKNOWN").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(5.0);
    }
}
