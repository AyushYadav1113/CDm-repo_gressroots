package com.grassroots.cdm.entity.enums;

/**
 * Execution state of a job dispatched to a ServiceNow MID Server.
 */
public enum MidServerExecutionState {
    QUEUED,
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    TIMED_OUT,
    CANCELLED
}
