package com.grassroots.cdm.deployment.adapter.linux.exception;

/**
 * Thrown when secure certificate transfer to the target Linux host fails.
 */
public class LinuxCertificateTransferException extends LinuxDeploymentException {

    private final String certificatePath;
    private final int exitCode;

    public LinuxCertificateTransferException(String message, String certificatePath, int exitCode) {
        super(message);
        this.certificatePath = certificatePath;
        this.exitCode = exitCode;
    }

    public String getCertificatePath() {
        return certificatePath;
    }

    public int getExitCode() {
        return exitCode;
    }
}
