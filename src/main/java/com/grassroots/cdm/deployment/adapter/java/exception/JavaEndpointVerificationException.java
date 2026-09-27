package com.grassroots.cdm.deployment.adapter.java.exception;

/**
 * Thrown when live TLS endpoint verification fails for a Java application,
 * e.g. handshake failure, connection refused, or TLS leaf thumbprint mismatch.
 */
public class JavaEndpointVerificationException extends JavaDeploymentException {

    private final String expectedThumbprint;
    private final String actualThumbprint;
    private final int port;

    public JavaEndpointVerificationException(String message, String expectedThumbprint, String actualThumbprint, int port) {
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
