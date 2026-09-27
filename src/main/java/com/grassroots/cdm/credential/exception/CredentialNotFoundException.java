package com.grassroots.cdm.credential.exception;

/**
 * Thrown when the requested account object or safe does not exist in CyberArk.
 */
public class CredentialNotFoundException extends CredentialException {

    public CredentialNotFoundException(String safeName, String accountName) {
        super(String.format("Credential not found in CyberArk Safe '%s' for Account '%s'", safeName, accountName),
                safeName, accountName);
    }

    public CredentialNotFoundException(String message, String safeName, String accountName) {
        super(message, safeName, accountName);
    }
}
