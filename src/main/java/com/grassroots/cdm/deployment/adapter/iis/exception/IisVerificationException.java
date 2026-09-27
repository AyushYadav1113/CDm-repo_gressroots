package com.grassroots.cdm.deployment.adapter.iis.exception;

/**
 * Thrown when post-deployment verification fails (e.g. thumbprint mismatch or SSL handshake error).
 */
public class IisVerificationException extends IisDeploymentException {

    private final String expectedThumbprint;
    private final String actualThumbprint;

    public IisVerificationException(String message, String expectedThumbprint, String actualThumbprint) {
        super(message);
        this.expectedThumbprint = expectedThumbprint;
        this.actualThumbprint = actualThumbprint;
    }

    public String getExpectedThumbprint() {
        return expectedThumbprint;
    }

    public String getActualThumbprint() {
        return actualThumbprint;
    }
}
