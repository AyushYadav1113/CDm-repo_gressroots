package com.grassroots.cdm.deployment.adapter.iis.exception;

/**
 * Thrown when updating or re-binding the SSL certificate on an IIS site fails.
 */
public class IisBindingUpdateException extends IisDeploymentException {

    private final String siteName;
    private final int port;
    private final int exitCode;

    public IisBindingUpdateException(String message, String siteName, int port, int exitCode) {
        super(message);
        this.siteName = siteName;
        this.port = port;
        this.exitCode = exitCode;
    }

    public String getSiteName() {
        return siteName;
    }

    public int getPort() {
        return port;
    }

    public int getExitCode() {
        return exitCode;
    }
}
