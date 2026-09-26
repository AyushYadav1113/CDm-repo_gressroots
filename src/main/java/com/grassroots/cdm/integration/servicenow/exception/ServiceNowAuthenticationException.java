package com.grassroots.cdm.integration.servicenow.exception;

/**
 * Thrown when ServiceNow rejects credentials or access tokens (HTTP 401 Unauthorized / HTTP 403 Forbidden).
 */
public class ServiceNowAuthenticationException extends ServiceNowException {

    public ServiceNowAuthenticationException(String message, int statusCode) {
        super(message, statusCode);
    }

    public ServiceNowAuthenticationException(String message, int statusCode, Throwable cause) {
        super(message, statusCode, cause);
    }
}
