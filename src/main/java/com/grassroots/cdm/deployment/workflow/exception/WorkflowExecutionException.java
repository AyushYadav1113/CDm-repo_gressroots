package com.grassroots.cdm.deployment.workflow.exception;

/**
 * Base exception for deployment workflow execution errors.
 */
public class WorkflowExecutionException extends RuntimeException {

    public WorkflowExecutionException(String message) {
        super(message);
    }

    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
