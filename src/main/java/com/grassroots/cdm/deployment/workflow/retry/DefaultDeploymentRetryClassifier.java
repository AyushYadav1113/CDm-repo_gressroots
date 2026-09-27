package com.grassroots.cdm.deployment.workflow.retry;

import com.grassroots.cdm.credential.exception.CredentialExpiredException;
import com.grassroots.cdm.credential.exception.CredentialNotFoundException;
import com.grassroots.cdm.credential.exception.CredentialRotationInProgressException;
import com.grassroots.cdm.credential.exception.CyberArkUnauthorizedException;
import com.grassroots.cdm.credential.exception.CyberArkUnavailableException;
import com.grassroots.cdm.deployment.workflow.exception.DuplicateProcessingException;
import com.grassroots.cdm.deployment.workflow.exception.InvalidStateTransitionException;
import com.grassroots.cdm.deployment.workflow.exception.JobConcurrencyException;
import com.grassroots.cdm.deployment.workflow.exception.MaxRetriesExceededException;
import com.grassroots.cdm.integration.midserver.exception.MidServerAuthenticationException;
import com.grassroots.cdm.integration.midserver.exception.MidServerExecutionTimeoutException;
import com.grassroots.cdm.integration.midserver.exception.MidServerTimeoutException;
import com.grassroots.cdm.integration.midserver.exception.MidServerUnavailableException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoAuthenticationException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoClientException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoRateLimitException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoServerException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoTimeoutException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Locale;

/**
 * Default component evaluating retry eligibility for deployment jobs.
 */
@Component
public class DefaultDeploymentRetryClassifier implements DeploymentRetryClassifier {

    @Override
    public boolean isRetryable(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        // 1. Explicit Non-Retryable Exceptions (Fail fast / manual review)
        if (throwable instanceof CyberArkUnauthorizedException
                || throwable instanceof CredentialNotFoundException
                || throwable instanceof CredentialExpiredException
                || throwable instanceof SectigoAuthenticationException
                || throwable instanceof SectigoClientException
                || throwable instanceof MidServerAuthenticationException
                || throwable instanceof InvalidStateTransitionException
                || throwable instanceof MaxRetriesExceededException
                || throwable instanceof IllegalArgumentException) {
            return false;
        }

        // 2. Explicit Retryable Exceptions
        if (throwable instanceof CredentialRotationInProgressException
                || throwable instanceof CyberArkUnavailableException
                || throwable instanceof MidServerUnavailableException
                || throwable instanceof MidServerTimeoutException
                || throwable instanceof MidServerExecutionTimeoutException
                || throwable instanceof SectigoTimeoutException
                || throwable instanceof SectigoServerException
                || throwable instanceof SectigoRateLimitException
                || throwable instanceof JobConcurrencyException
                || throwable instanceof DuplicateProcessingException
                || throwable instanceof OptimisticLockingFailureException
                || throwable instanceof ConnectException
                || throwable instanceof SocketTimeoutException) {
            return true;
        }

        // 3. Inspect cause recursively if present
        if (throwable.getCause() != null && throwable.getCause() != throwable) {
            if (isRetryable(throwable.getCause())) {
                return true;
            }
        }

        // 4. IOExceptions are generally transient unless identified otherwise
        if (throwable instanceof IOException) {
            return true;
        }

        // 5. Fall back to message inspection
        return isRetryable(throwable.getMessage());
    }

    @Override
    public boolean isRetryable(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return false;
        }

        String msg = errorMessage.toLowerCase(Locale.ROOT);

        // Explicit fatal patterns
        if (msg.contains("unauthorized")
                || msg.contains("forbidden")
                || msg.contains("401")
                || msg.contains("403")
                || msg.contains("not found")
                || msg.contains("404")
                || msg.contains("expired")
                || msg.contains("mismatch")
                || msg.contains("invalid configuration")
                || msg.contains("invalid state transition")
                || msg.contains("max retries")) {
            return false;
        }

        // Explicit transient patterns
        return msg.contains("timeout")
                || msg.contains("timed out")
                || msg.contains("connection refused")
                || msg.contains("connect reset")
                || msg.contains("connection reset")
                || msg.contains("503")
                || msg.contains("502")
                || msg.contains("504")
                || msg.contains("429")
                || msg.contains("rate limit")
                || msg.contains("rotation in progress")
                || msg.contains("temporarily unavailable")
                || msg.contains("service unavailable")
                || msg.contains("transient")
                || msg.contains("network error");
    }
}
