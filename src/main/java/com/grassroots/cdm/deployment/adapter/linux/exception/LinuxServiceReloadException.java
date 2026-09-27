package com.grassroots.cdm.deployment.adapter.linux.exception;

/**
 * Thrown when graceful service reload (e.g. systemctl reload apache2 / nginx) fails.
 */
public class LinuxServiceReloadException extends LinuxDeploymentException {

    private final String reloadCommand;
    private final int exitCode;

    public LinuxServiceReloadException(String message, String reloadCommand, int exitCode) {
        super(message);
        this.reloadCommand = reloadCommand;
        this.exitCode = exitCode;
    }

    public String getReloadCommand() {
        return reloadCommand;
    }

    public int getExitCode() {
        return exitCode;
    }
}
