package com.grassroots.cdm.exception;

/**
 * Thrown when an error occurs during communication with external systems
 * (e.g. ServiceNow, Sectigo, CyberArk, MID Server).
 */
public class IntegrationException extends CdmException {

    private final String systemName;

    public IntegrationException(String systemName, String message) {
        super(String.format("[%s] %s", systemName, message));
        this.systemName = systemName;
    }

    public IntegrationException(String systemName, String message, Throwable cause) {
        super(String.format("[%s] %s", systemName, message), cause);
        this.systemName = systemName;
    }

    public String getSystemName() {
        return systemName;
    }
}
