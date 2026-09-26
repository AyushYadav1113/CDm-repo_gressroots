package com.grassroots.cdm.integration.servicenow.exception;

/**
 * Thrown when ServiceNow returns an unexpected 4xx client response.
 */
public class ServiceNowClientException extends ServiceNowException {

    public ServiceNowClientException(String message, int statusCode) {
        super(message, statusCode);
    }

    public ServiceNowClientException(String message, int statusCode, Throwable cause) {
        super(message, statusCode, cause);
    }
}
