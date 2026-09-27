package com.grassroots.cdm.deployment.adapter.linux.exception;

/**
 * Thrown when modifying web server certificate configuration file directives fails.
 */
public class LinuxConfigurationUpdateException extends LinuxDeploymentException {

    private final String configPath;
    private final int exitCode;

    public LinuxConfigurationUpdateException(String message, String configPath, int exitCode) {
        super(message);
        this.configPath = configPath;
        this.exitCode = exitCode;
    }

    public String getConfigPath() {
        return configPath;
    }

    public int getExitCode() {
        return exitCode;
    }
}
