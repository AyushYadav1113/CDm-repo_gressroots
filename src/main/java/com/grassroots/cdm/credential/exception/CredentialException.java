package com.grassroots.cdm.credential.exception;

import com.grassroots.cdm.exception.IntegrationException;

/**
 * Base unchecked exception for all credential management and vault retrieval errors.
 */
public class CredentialException extends IntegrationException {

    private final String safeName;
    private final String accountName;

    public CredentialException(String message) {
        super("CyberArk", message);
        this.safeName = null;
        this.accountName = null;
    }

    public CredentialException(String message, Throwable cause) {
        super("CyberArk", message, cause);
        this.safeName = null;
        this.accountName = null;
    }

    public CredentialException(String message, String safeName, String accountName) {
        super("CyberArk", String.format("%s (Safe=%s, Account=%s)", message, safeName, accountName));
        this.safeName = safeName;
        this.accountName = accountName;
    }

    public CredentialException(String message, String safeName, String accountName, Throwable cause) {
        super("CyberArk", String.format("%s (Safe=%s, Account=%s)", message, safeName, accountName), cause);
        this.safeName = safeName;
        this.accountName = accountName;
    }

    public String getSafeName() {
        return safeName;
    }

    public String getAccountName() {
        return accountName;
    }
}
