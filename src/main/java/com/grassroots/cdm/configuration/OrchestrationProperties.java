package com.grassroots.cdm.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for operational workflow orchestration, schedulers, and retry policies.
 */
@Component
@ConfigurationProperties(prefix = "cdm.orchestration")
public class OrchestrationProperties {

    private boolean schedulingEnabled = true;

    private SchedulerSettings discovery = new SchedulerSettings(true, "0 0 1 * * ?");
    private SchedulerSettings sectigoSync = new SchedulerSettings(true, "0 30 1 * * ?");
    private SchedulerSettings matching = new SchedulerSettings(true, "0 0 2 * * ?");
    private SchedulerSettings planning = new SchedulerSettings(true, "0 30 2 * * ?");
    private FixedDelaySettings execution = new FixedDelaySettings(true, 60000);
    private FixedDelaySettings retry = new FixedDelaySettings(true, 30000);
    private SchedulerSettings nextDayConfirmation = new SchedulerSettings(true, "0 0 4 * * ?");

    private RetrySettings retryPolicy = new RetrySettings();

    public boolean isSchedulingEnabled() {
        return schedulingEnabled;
    }

    public void setSchedulingEnabled(boolean schedulingEnabled) {
        this.schedulingEnabled = schedulingEnabled;
    }

    public SchedulerSettings getDiscovery() {
        return discovery;
    }

    public void setDiscovery(SchedulerSettings discovery) {
        this.discovery = discovery;
    }

    public SchedulerSettings getSectigoSync() {
        return sectigoSync;
    }

    public void setSectigoSync(SchedulerSettings sectigoSync) {
        this.sectigoSync = sectigoSync;
    }

    public SchedulerSettings getMatching() {
        return matching;
    }

    public void setMatching(SchedulerSettings matching) {
        this.matching = matching;
    }

    public SchedulerSettings getPlanning() {
        return planning;
    }

    public void setPlanning(SchedulerSettings planning) {
        this.planning = planning;
    }

    public FixedDelaySettings getExecution() {
        return execution;
    }

    public void setExecution(FixedDelaySettings execution) {
        this.execution = execution;
    }

    public FixedDelaySettings getRetry() {
        return retry;
    }

    public void setRetry(FixedDelaySettings retry) {
        this.retry = retry;
    }

    public SchedulerSettings getNextDayConfirmation() {
        return nextDayConfirmation;
    }

    public void setNextDayConfirmation(SchedulerSettings nextDayConfirmation) {
        this.nextDayConfirmation = nextDayConfirmation;
    }

    public RetrySettings getRetryPolicy() {
        return retryPolicy;
    }

    public void setRetryPolicy(RetrySettings retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    public static class SchedulerSettings {
        private boolean enabled = true;
        private String cron;

        public SchedulerSettings() {}

        public SchedulerSettings(boolean enabled, String cron) {
            this.enabled = enabled;
            this.cron = cron;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }

    public static class FixedDelaySettings {
        private boolean enabled = true;
        private long fixedDelayMs = 60000;

        public FixedDelaySettings() {}

        public FixedDelaySettings(boolean enabled, long fixedDelayMs) {
            this.enabled = enabled;
            this.fixedDelayMs = fixedDelayMs;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }
    }

    public static class RetrySettings {
        private int maxAttempts = 3;
        private Duration initialInterval = Duration.ofSeconds(30);
        private double multiplier = 2.0;
        private Duration maxInterval = Duration.ofHours(1);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialInterval() {
            return initialInterval;
        }

        public void setInitialInterval(Duration initialInterval) {
            this.initialInterval = initialInterval;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }

        public Duration getMaxInterval() {
            return maxInterval;
        }

        public void setMaxInterval(Duration maxInterval) {
            this.maxInterval = maxInterval;
        }
    }
}
