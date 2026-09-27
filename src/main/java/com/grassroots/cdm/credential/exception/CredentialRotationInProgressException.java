package com.grassroots.cdm.credential.exception;

/**
 * Thrown when CyberArk Central Policy Manager (CPM) is currently in the process
 * of rotating the requested account's password (PasswordChangeInProcess = true or HTTP 409).
 */
public class CredentialRotationInProgressException extends CredentialException {

    private final boolean retryable;

    public CredentialRotationInProgressException(String safeName, String accountName) {
        super(String.format("Password rotation is currently in progress for account '%s' in safe '%s'. Retrieval deferred.",
                accountName, safeName), safeName, accountName);
        this.retryable = true;
    }

    public CredentialRotationInProgressException(String message, String safeName, String accountName) {
        super(message, safeName, accountName);
        this.retryable = true;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
