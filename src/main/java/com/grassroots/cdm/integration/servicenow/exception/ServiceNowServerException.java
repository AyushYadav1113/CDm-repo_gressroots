package com.grassroots.cdm.integration.servicenow.exception;

/**
 * Thrown when ServiceNow returns an HTTP 5xx server fault or is temporarily unavailable.
 */
public class ServiceNowServerException extends ServiceNowException {

    public ServiceNowServerException(String message, int statusCode) {
        super(message, statusCode);
    }

    public ServiceNowServerException(String message, int statusCode, Throwable cause) {
        super(message, statusCode, cause);
    }
}
