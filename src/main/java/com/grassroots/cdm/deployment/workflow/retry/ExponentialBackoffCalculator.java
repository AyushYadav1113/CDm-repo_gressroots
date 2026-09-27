package com.grassroots.cdm.deployment.workflow.retry;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Calculates exponential backoff intervals for failed deployment jobs.
 *
 * Formula:
 * interval = min(maxInterval, initialInterval * (multiplier ^ (attempt - 1)))
 */
@Component
public class ExponentialBackoffCalculator {

    private final Duration initialInterval;
    private final double multiplier;
    private final Duration maxInterval;

    public ExponentialBackoffCalculator() {
        this(Duration.ofSeconds(30), 2.0, Duration.ofHours(1));
    }

    public ExponentialBackoffCalculator(Duration initialInterval, double multiplier, Duration maxInterval) {
        this.initialInterval = (initialInterval != null) ? initialInterval : Duration.ofSeconds(30);
        this.multiplier = (multiplier >= 1.0) ? multiplier : 2.0;
        this.maxInterval = (maxInterval != null) ? maxInterval : Duration.ofHours(1);
    }

    /**
     * Calculates the next execution timestamp based on attempt index.
     *
     * @param attemptCount current attempt (1-based)
     * @param fromTime     baseline timestamp (usually Instant.now())
     * @return next scheduled Instant
     */
    public Instant calculateNextRetryTime(int attemptCount, Instant fromTime) {
        Instant base = (fromTime != null) ? fromTime : Instant.now();
        Duration delay = calculateBackoffDelay(attemptCount);
        return base.plus(delay);
    }

    /**
     * Calculates backoff duration for the given attempt count.
     */
    public Duration calculateBackoffDelay(int attemptCount) {
        int attempt = Math.max(1, attemptCount);
        double factor = Math.pow(multiplier, attempt - 1);
        long delayMillis = (long) (initialInterval.toMillis() * factor);

        if (delayMillis < 0 || delayMillis > maxInterval.toMillis()) {
            return maxInterval;
        }

        return Duration.ofMillis(delayMillis);
    }

    public Duration getInitialInterval() {
        return initialInterval;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public Duration getMaxInterval() {
        return maxInterval;
    }
}
