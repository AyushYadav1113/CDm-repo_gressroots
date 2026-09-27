package com.grassroots.cdm.deployment.adapter.iis.exception;

/**
 * Thrown when importing the certificate bundle/PFX into the Windows LocalMachine certificate store fails.
 */
public class IisCertificateImportException extends IisDeploymentException {

    private final String thumbprint;
    private final int exitCode;

    public IisCertificateImportException(String message, String thumbprint, int exitCode) {
        super(message);
        this.thumbprint = thumbprint;
        this.exitCode = exitCode;
    }

    public String getThumbprint() {
        return thumbprint;
    }

    public int getExitCode() {
        return exitCode;
    }
}
