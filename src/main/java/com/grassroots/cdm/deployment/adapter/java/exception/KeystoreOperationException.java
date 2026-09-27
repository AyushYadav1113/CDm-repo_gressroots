package com.grassroots.cdm.deployment.adapter.java.exception;

/**
 * Thrown when creating, opening, updating, or saving a JKS or PKCS12 keystore fails.
 */
public class KeystoreOperationException extends JavaDeploymentException {

    private final String keystoreLocation;
    private final String alias;
    private final int exitCode;

    public KeystoreOperationException(String message, String keystoreLocation, String alias, int exitCode) {
        super(message);
        this.keystoreLocation = keystoreLocation;
        this.alias = alias;
        this.exitCode = exitCode;
    }

    public KeystoreOperationException(String message, String keystoreLocation, String alias, int exitCode, Throwable cause) {
        super(message, cause);
        this.keystoreLocation = keystoreLocation;
        this.alias = alias;
        this.exitCode = exitCode;
    }

    public String getKeystoreLocation() {
        return keystoreLocation;
    }

    public String getAlias() {
        return alias;
    }

    public int getExitCode() {
        return exitCode;
    }
}
