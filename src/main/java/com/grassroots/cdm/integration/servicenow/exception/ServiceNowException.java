package com.grassroots.cdm.integration.servicenow.exception;

import com.grassroots.cdm.exception.IntegrationException;

/**
 * Base exception for all ServiceNow integration faults.
 */
public class ServiceNowException extends IntegrationException {

    private final Integer statusCode;

    public ServiceNowException(String message) {
        super("SERVICENOW", message);
        this.statusCode = null;
    }

    public ServiceNowException(String message, Throwable cause) {
        super("SERVICENOW", message, cause);
        this.statusCode = null;
    }

    public ServiceNowException(String message, int statusCode) {
        super("SERVICENOW", String.format("%s (HTTP %d)", message, statusCode));
        this.statusCode = statusCode;
    }

    public ServiceNowException(String message, int statusCode, Throwable cause) {
        super("SERVICENOW", String.format("%s (HTTP %d)", message, statusCode), cause);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
