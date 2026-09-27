package com.grassroots.cdm.deployment.adapter.linux.exception;

/**
 * Thrown when secure private key transfer to the target Linux host fails.
 * Ensures no private key material is included in the exception message.
 */
public class LinuxPrivateKeyTransferException extends LinuxDeploymentException {

    private final String privateKeyPath;
    private final int exitCode;

    public LinuxPrivateKeyTransferException(String message, String privateKeyPath, int exitCode) {
        super(message);
        this.privateKeyPath = privateKeyPath;
        this.exitCode = exitCode;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public int getExitCode() {
        return exitCode;
    }
}
