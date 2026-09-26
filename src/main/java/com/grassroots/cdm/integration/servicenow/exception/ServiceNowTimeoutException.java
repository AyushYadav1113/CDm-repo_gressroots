package com.grassroots.cdm.integration.servicenow.exception;

/**
 * Thrown when an HTTP connection or read timeout occurs communicating with ServiceNow.
 */
public class ServiceNowTimeoutException extends ServiceNowException {

    public ServiceNowTimeoutException(String message) {
        super(message);
    }

    public ServiceNowTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
