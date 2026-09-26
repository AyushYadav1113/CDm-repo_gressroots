package com.grassroots.cdm.integration.servicenow.exception;

/**
 * Thrown when ServiceNow returns malformed, unparseable, or schema-violating JSON payloads.
 */
public class ServiceNowParseException extends ServiceNowException {

    public ServiceNowParseException(String message) {
        super(message);
    }

    public ServiceNowParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
