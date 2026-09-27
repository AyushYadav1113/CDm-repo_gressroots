package com.grassroots.cdm.credential.exception;

/**
 * Thrown when CyberArk Central Credential Provider (CCP) / AIM service is unreachable,
 * offline, or times out.
 */
public class CyberArkUnavailableException extends CredentialException {

    private final String endpointUrl;

    public CyberArkUnavailableException(String message, String endpointUrl, Throwable cause) {
        super(String.format("CyberArk service unavailable at %s: %s", endpointUrl, message), cause);
        this.endpointUrl = endpointUrl;
    }

    public CyberArkUnavailableException(String message, String endpointUrl) {
        super(String.format("CyberArk service unavailable at %s: %s", endpointUrl, message));
        this.endpointUrl = endpointUrl;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }
}
