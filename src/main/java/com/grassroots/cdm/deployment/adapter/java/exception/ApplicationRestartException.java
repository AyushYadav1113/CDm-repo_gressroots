package com.grassroots.cdm.deployment.adapter.java.exception;

/**
 * Thrown when restarting or reloading a Java application or service fails.
 */
public class ApplicationRestartException extends JavaDeploymentException {

    private final String restartCommandOrStrategy;
    private final int exitCode;

    public ApplicationRestartException(String message, String restartCommandOrStrategy, int exitCode) {
        super(message);
        this.restartCommandOrStrategy = restartCommandOrStrategy;
        this.exitCode = exitCode;
    }

    public String getRestartCommandOrStrategy() {
        return restartCommandOrStrategy;
    }

    public int getExitCode() {
        return exitCode;
    }
}
