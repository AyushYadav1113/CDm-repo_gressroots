package com.grassroots.cdm.deployment.adapter.linux.exception;

/**
 * Thrown when web server configuration validation (e.g. apache2ctl -t, nginx -t)
 * detects syntax errors or invalid configuration directives.
 */
public class LinuxConfigurationValidationException extends LinuxDeploymentException {

    private final String validationCommand;
    private final String outputDetails;
    private final int exitCode;

    public LinuxConfigurationValidationException(String message, String validationCommand, String outputDetails, int exitCode) {
        super(message);
        this.validationCommand = validationCommand;
        this.outputDetails = outputDetails;
        this.exitCode = exitCode;
    }

    public String getValidationCommand() {
        return validationCommand;
    }

    public String getOutputDetails() {
        return outputDetails;
    }

    public int getExitCode() {
        return exitCode;
    }
}
