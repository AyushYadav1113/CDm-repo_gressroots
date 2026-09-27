package com.grassroots.cdm.deployment.workflow.retry;

/**
 * Service contract for determining whether a deployment workflow failure is genuinely retryable.
 *
 * Adheres strictly to the architectural policy:
 * - Retry only transient network, timeout, rate limit, or lock/rotation conditions.
 * - Never retry fatal authorization, missing credential, invalid config, or certificate mismatch failures.
 */
public interface DeploymentRetryClassifier {

    /**
     * Determines whether an exception represents a retryable transient failure.
     *
     * @param throwable error thrown during workflow step execution
     * @return true if eligible for automatic exponential backoff retry
     */
    boolean isRetryable(Throwable throwable);

    /**
     * Determines whether an error message text represents a retryable transient condition.
     *
     * @param errorMessage diagnostic error description
     * @return true if eligible for automatic retry
     */
    boolean isRetryable(String errorMessage);
}
