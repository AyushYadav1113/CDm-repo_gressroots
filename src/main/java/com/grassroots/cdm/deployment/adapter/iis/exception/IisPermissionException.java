package com.grassroots.cdm.deployment.adapter.iis.exception;

/**
 * Thrown when granting private key file permissions (ACLs) to IIS identities (e.g. IIS_IUSRS) fails.
 */
public class IisPermissionException extends IisDeploymentException {

    private final String account;
    private final int exitCode;

    public IisPermissionException(String message, String account, int exitCode) {
        super(message);
        this.account = account;
        this.exitCode = exitCode;
    }

    public String getAccount() {
        return account;
    }

    public int getExitCode() {
        return exitCode;
    }
}
