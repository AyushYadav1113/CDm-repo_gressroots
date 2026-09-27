package com.grassroots.cdm.credential.exception;

/**
 * Thrown when CyberArk CCP rejects authorization (e.g. invalid AppID, missing safe permissions,
 * unauthorized IP address, or certificate rejection).
 */
public class CyberArkUnauthorizedException extends CredentialException {

    private final int statusCode;
    private final String appId;

    public CyberArkUnauthorizedException(String message, String appId, String safeName, int statusCode) {
        super(String.format("CyberArk authorization denied for AppID '%s' on Safe '%s': %s (HTTP %d)",
                appId, safeName, message, statusCode), safeName, null);
        this.statusCode = statusCode;
        this.appId = appId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getAppId() {
        return appId;
    }
}
