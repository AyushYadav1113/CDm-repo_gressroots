package com.grassroots.cdm.deployment.workflow.retry;

import com.grassroots.cdm.credential.exception.CredentialExpiredException;
import com.grassroots.cdm.credential.exception.CredentialNotFoundException;
import com.grassroots.cdm.credential.exception.CredentialRotationInProgressException;
import com.grassroots.cdm.credential.exception.CyberArkUnauthorizedException;
import com.grassroots.cdm.credential.exception.CyberArkUnavailableException;
import com.grassroots.cdm.deployment.workflow.exception.InvalidStateTransitionException;
import com.grassroots.cdm.deployment.workflow.exception.MaxRetriesExceededException;
import com.grassroots.cdm.integration.midserver.exception.MidServerAuthenticationException;
import com.grassroots.cdm.integration.midserver.exception.MidServerTimeoutException;
import com.grassroots.cdm.integration.midserver.exception.MidServerUnavailableException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoAuthenticationException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoRateLimitException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.OptimisticLockingFailureException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DefaultDeploymentRetryClassifier.
 * Ensures only genuinely transient failures are retried.
 */
class DeploymentRetryClassifierTest {

    private DefaultDeploymentRetryClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new DefaultDeploymentRetryClassifier();
    }

    // ─── Fatal / Non-Retryable Exceptions ────────────────────────────────────

    @Test
    void returns_false_for_CyberArkUnauthorizedException() {
        assertThat(classifier.isRetryable(
                new CyberArkUnauthorizedException("Forbidden", "CDM_APP", "SAFE", 403))).isFalse();
    }

    @Test
    void returns_false_for_CredentialNotFoundException() {
        assertThat(classifier.isRetryable(new CredentialNotFoundException("safe", "acct"))).isFalse();
    }

    @Test
    void returns_false_for_CredentialExpiredException() {
        assertThat(classifier.isRetryable(new CredentialExpiredException("safe", "acct"))).isFalse();
    }

    @Test
    void returns_false_for_SectigoAuthenticationException() {
        assertThat(classifier.isRetryable(new SectigoAuthenticationException("Unauthorized", 401))).isFalse();
    }

    @Test
    void returns_false_for_MidServerAuthenticationException() {
        assertThat(classifier.isRetryable(new MidServerAuthenticationException("Forbidden", 403))).isFalse();
    }

    @Test
    void returns_false_for_MaxRetriesExceededException() {
        assertThat(classifier.isRetryable(new MaxRetriesExceededException(UUID.randomUUID(), 3, 3, "Exceeded"))).isFalse();
    }

    @Test
    void returns_false_for_IllegalArgumentException() {
        assertThat(classifier.isRetryable(new IllegalArgumentException("Invalid config"))).isFalse();
    }

    @Test
    void returns_false_for_null_throwable() {
        assertThat(classifier.isRetryable((Throwable) null)).isFalse();
    }

    // ─── Retryable Exceptions ─────────────────────────────────────────────────

    @Test
    void returns_true_for_CredentialRotationInProgressException() {
        assertThat(classifier.isRetryable(new CredentialRotationInProgressException("safe", "acct"))).isTrue();
    }

    @Test
    void returns_true_for_CyberArkUnavailableException() {
        assertThat(classifier.isRetryable(new CyberArkUnavailableException("Unavailable", null))).isTrue();
    }

    @Test
    void returns_true_for_MidServerUnavailableException() {
        assertThat(classifier.isRetryable(new MidServerUnavailableException("MID down", "mid-server.internal"))).isTrue();
    }

    @Test
    void returns_true_for_MidServerTimeoutException() {
        assertThat(classifier.isRetryable(new MidServerTimeoutException("Timed out", 30000L))).isTrue();
    }

    @Test
    void returns_true_for_SectigoRateLimitException() {
        assertThat(classifier.isRetryable(new SectigoRateLimitException("Rate limit", 60L))).isTrue();
    }

    @Test
    void returns_true_for_SectigoServerException() {
        assertThat(classifier.isRetryable(new SectigoServerException("Server error", 503))).isTrue();
    }

    @Test
    void returns_true_for_OptimisticLockingFailureException() {
        assertThat(classifier.isRetryable(new OptimisticLockingFailureException("Concurrent edit"))).isTrue();
    }

    @Test
    void returns_true_for_ConnectException() {
        assertThat(classifier.isRetryable(new ConnectException("Connection refused"))).isTrue();
    }

    @Test
    void returns_true_for_SocketTimeoutException() {
        assertThat(classifier.isRetryable(new SocketTimeoutException("Read timed out"))).isTrue();
    }

    @Test
    void returns_true_for_generic_IOException() {
        assertThat(classifier.isRetryable(new IOException("Network I/O error"))).isTrue();
    }

    // ─── String-based Classification ─────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"Connection refused", "Service Unavailable 503", "timeout occurred",
            "timed out", "connection reset", "rate limit exceeded",
            "rotation in progress", "temporarily unavailable"})
    void returns_true_for_retryable_error_messages(String message) {
        assertThat(classifier.isRetryable(message)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Unauthorized 401", "Forbidden 403", "not found 404",
            "invalid configuration", "expired credential", "mismatch", "max retries"})
    void returns_false_for_fatal_error_messages(String message) {
        assertThat(classifier.isRetryable(message)).isFalse();
    }

    @Test
    void returns_false_for_null_error_message() {
        assertThat(classifier.isRetryable((String) null)).isFalse();
    }

    @Test
    void returns_false_for_blank_error_message() {
        assertThat(classifier.isRetryable("  ")).isFalse();
    }

    // ─── Nested Cause Classification ─────────────────────────────────────────

    @Test
    void returns_true_when_nested_cause_is_retryable() {
        RuntimeException wrapper = new RuntimeException("Wrapper", new ConnectException("Connection refused"));
        assertThat(classifier.isRetryable(wrapper)).isTrue();
    }

    @Test
    void returns_false_when_nested_cause_is_fatal() {
        RuntimeException wrapper = new RuntimeException("Wrapper", new IllegalArgumentException("Invalid config"));
        // The outer message doesn't match retryable, cause is fatal, wrapper message doesn't match retryable patterns
        assertThat(classifier.isRetryable(wrapper)).isFalse();
    }
}
