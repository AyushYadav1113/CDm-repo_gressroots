package com.grassroots.cdm.deployment.adapter.linux.exception;

/**
 * Thrown when live TLS endpoint verification fails after certificate deployment,
 * e.g. handshake failure, connection refused, or TLS thumbprint mismatch.
 */
public class LinuxVerificationException extends LinuxDeploymentException {

    private final String expectedThumbprint;
    private final String actualThumbprint;
    private final int port;

    public LinuxVerificationException(String message, String expectedThumbprint, String actualThumbprint, int port) {
        super(message);
        this.expectedThumbprint = expectedThumbprint;
        this.actualThumbprint = actualThumbprint;
        this.port = port;
    }

    public String getExpectedThumbprint() {
        return expectedThumbprint;
    }

    public String getActualThumbprint() {
        return actualThumbprint;
    }

    public int getPort() {
        return port;
    }
}
