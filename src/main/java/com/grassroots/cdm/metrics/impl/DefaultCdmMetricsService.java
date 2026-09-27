package com.grassroots.cdm.metrics.impl;

import com.grassroots.cdm.metrics.CdmMetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Production implementation of {@link CdmMetricsService} that publishes operational
 * telemetry to Micrometer {@link MeterRegistry} (exposed via Spring Boot Actuator).
 */
@Service
public class DefaultCdmMetricsService implements CdmMetricsService {

    public static final String METRIC_CERTIFICATES_DISCOVERED = "cdm.certificates.discovered";
    public static final String METRIC_CERTIFICATES_MATCHED = "cdm.certificates.matched";
    public static final String METRIC_DEPLOYMENT_JOBS_CREATED = "cdm.deployment.jobs.created";
    public static final String METRIC_DEPLOYMENTS_SUCCESSFUL = "cdm.deployments.successful";
    public static final String METRIC_DEPLOYMENTS_FAILED = "cdm.deployments.failed";
    public static final String METRIC_VERIFICATION_FAILURES = "cdm.verification.failures";
    public static final String METRIC_MID_SERVER_FAILURES = "cdm.midserver.failures";
    public static final String METRIC_RETRY_COUNTS = "cdm.retry.counts";
    public static final String METRIC_WORKFLOW_DURATION = "cdm.workflow.duration";

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    public DefaultCdmMetricsService(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordCertificatesDiscovered(String source, int count) {
        if (count <= 0) return;
        String safeSource = (source != null && !source.isBlank()) ? source.toUpperCase() : "UNKNOWN";
        getCounter(METRIC_CERTIFICATES_DISCOVERED, "source", safeSource).increment(count);
    }

    @Override
    public void recordCertificatesMatched(String decision, int count) {
        if (count <= 0) return;
        String safeDecision = (decision != null && !decision.isBlank()) ? decision.toUpperCase() : "UNKNOWN";
        getCounter(METRIC_CERTIFICATES_MATCHED, "decision", safeDecision).increment(count);
    }

    @Override
    public void recordDeploymentJobCreated(String technology) {
        String safeTech = (technology != null && !technology.isBlank()) ? technology.toUpperCase() : "GENERIC";
        getCounter(METRIC_DEPLOYMENT_JOBS_CREATED, "technology", safeTech).increment();
    }

    @Override
    public void recordDeploymentSuccessful(String technology) {
        String safeTech = (technology != null && !technology.isBlank()) ? technology.toUpperCase() : "GENERIC";
        getCounter(METRIC_DEPLOYMENTS_SUCCESSFUL, "technology", safeTech).increment();
    }

    @Override
    public void recordDeploymentFailed(String failureType, String technology) {
        String safeType = (failureType != null && !failureType.isBlank()) ? failureType : "UNKNOWN_ERROR";
        String safeTech = (technology != null && !technology.isBlank()) ? technology.toUpperCase() : "GENERIC";
        getCounter(METRIC_DEPLOYMENTS_FAILED, "failure_type", safeType, "technology", safeTech).increment();
    }

    @Override
    public void recordVerificationFailure(String reason) {
        String safeReason = (reason != null && !reason.isBlank()) ? reason : "UNSPECIFIED";
        getCounter(METRIC_VERIFICATION_FAILURES, "reason", safeReason).increment();
    }

    @Override
    public void recordMidServerFailure(String targetType) {
        String safeTarget = (targetType != null && !targetType.isBlank()) ? targetType.toUpperCase() : "UNKNOWN";
        getCounter(METRIC_MID_SERVER_FAILURES, "target_type", safeTarget).increment();
    }

    @Override
    public void recordRetryAttempt(int attemptNumber) {
        getCounter(METRIC_RETRY_COUNTS, "attempt", String.valueOf(attemptNumber)).increment();
    }

    @Override
    public void recordWorkflowDuration(String workflowName, Duration duration) {
        if (duration == null || duration.isNegative()) return;
        String safeWorkflow = (workflowName != null && !workflowName.isBlank()) ? workflowName : "generic";
        getTimer(METRIC_WORKFLOW_DURATION, "workflow", safeWorkflow).record(duration);
    }

    @Override
    public void recordTimedWorkflow(String workflowName, Runnable action) {
        Timer timer = getTimer(METRIC_WORKFLOW_DURATION, "workflow", workflowName != null ? workflowName : "generic");
        timer.record(action);
    }

    @Override
    public <T> T recordTimedWorkflow(String workflowName, Supplier<T> supplier) {
        Timer timer = getTimer(METRIC_WORKFLOW_DURATION, "workflow", workflowName != null ? workflowName : "generic");
        return timer.record(supplier);
    }

    private Counter getCounter(String name, String... tags) {
        String cacheKey = buildKey(name, tags);
        return counterCache.computeIfAbsent(cacheKey, k -> Counter.builder(name)
                .tags(tags)
                .register(registry));
    }

    private Timer getTimer(String name, String... tags) {
        String cacheKey = buildKey(name, tags);
        return timerCache.computeIfAbsent(cacheKey, k -> Timer.builder(name)
                .tags(tags)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry));
    }

    private String buildKey(String name, String... tags) {
        StringBuilder sb = new StringBuilder(name);
        if (tags != null) {
            for (String tag : tags) {
                sb.append(':').append(tag);
            }
        }
        return sb.toString();
    }
}
