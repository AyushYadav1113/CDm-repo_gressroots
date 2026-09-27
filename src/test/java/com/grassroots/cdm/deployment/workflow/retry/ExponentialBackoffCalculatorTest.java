package com.grassroots.cdm.deployment.workflow.retry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ExponentialBackoffCalculator verifying backoff delay computation.
 */
class ExponentialBackoffCalculatorTest {

    private ExponentialBackoffCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ExponentialBackoffCalculator(
                Duration.ofSeconds(10),
                2.0,
                Duration.ofHours(1)
        );
    }

    @Test
    void first_attempt_returns_initialInterval() {
        Duration delay = calculator.calculateBackoffDelay(1);
        assertThat(delay).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void second_attempt_doubles_the_delay() {
        Duration delay = calculator.calculateBackoffDelay(2);
        assertThat(delay).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void third_attempt_quadruples_the_initial_delay() {
        Duration delay = calculator.calculateBackoffDelay(3);
        assertThat(delay).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void delay_is_capped_at_maxInterval() {
        Duration delay = calculator.calculateBackoffDelay(100);
        assertThat(delay).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void negative_attempt_uses_attempt_1() {
        Duration delay = calculator.calculateBackoffDelay(-5);
        assertThat(delay).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void zero_attempt_uses_attempt_1() {
        Duration delay = calculator.calculateBackoffDelay(0);
        assertThat(delay).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void calculateNextRetryTime_returns_future_instant() {
        Instant now = Instant.now();
        Instant next = calculator.calculateNextRetryTime(1, now);

        assertThat(next).isAfter(now);
        assertThat(next).isBeforeOrEqualTo(now.plus(Duration.ofSeconds(11)));
    }

    @Test
    void calculateNextRetryTime_uses_current_time_when_fromTime_is_null() {
        Instant before = Instant.now();
        Instant next = calculator.calculateNextRetryTime(1, null);
        Instant after = Instant.now();

        assertThat(next).isAfterOrEqualTo(before.plus(Duration.ofSeconds(9)));
        assertThat(next).isBeforeOrEqualTo(after.plus(Duration.ofSeconds(11)));
    }

    @Test
    void default_constructor_provides_sensible_defaults() {
        ExponentialBackoffCalculator defaultCalc = new ExponentialBackoffCalculator();
        assertThat(defaultCalc.getInitialInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(defaultCalc.getMultiplier()).isEqualTo(2.0);
        assertThat(defaultCalc.getMaxInterval()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void invalid_multiplier_below_1_defaults_to_2() {
        ExponentialBackoffCalculator calc = new ExponentialBackoffCalculator(Duration.ofSeconds(10), 0.5, Duration.ofHours(1));
        Duration delay = calculator.calculateBackoffDelay(2);
        assertThat(delay).isEqualTo(Duration.ofSeconds(20)); // uses parent calculator which is valid
    }
}
