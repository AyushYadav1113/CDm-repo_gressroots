package com.grassroots.cdm.credential.exception;

/**
 * Thrown when the retrieved CyberArk credential has expired according to CPM policy
 * and cannot be safely used for deployment without rotation.
 */
public class CredentialExpiredException extends CredentialException {

    public CredentialExpiredException(String safeName, String accountName, String expirationDetails) {
        super(String.format("Credential for account '%s' in safe '%s' is expired: %s",
                accountName, safeName, expirationDetails), safeName, accountName);
    }

    public CredentialExpiredException(String safeName, String accountName) {
        super(String.format("Credential for account '%s' in safe '%s' is expired",
                accountName, safeName), safeName, accountName);
    }
}
