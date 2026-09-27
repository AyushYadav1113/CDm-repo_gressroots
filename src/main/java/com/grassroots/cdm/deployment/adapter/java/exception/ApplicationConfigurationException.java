package com.grassroots.cdm.deployment.adapter.java.exception;

/**
 * Thrown when modifying Java application configuration references (e.g. server.xml, application.yml) fails.
 */
public class ApplicationConfigurationException extends JavaDeploymentException {

    private final String appConfigLocation;
    private final int exitCode;

    public ApplicationConfigurationException(String message, String appConfigLocation, int exitCode) {
        super(message);
        this.appConfigLocation = appConfigLocation;
        this.exitCode = exitCode;
    }

    public String getAppConfigLocation() {
        return appConfigLocation;
    }

    public int getExitCode() {
        return exitCode;
    }
}
