package com.grassroots.cdm.audit;

/**
 * Standardized audit action types for traceability and compliance.
 */
public enum AuditAction {
    CERTIFICATE_DISCOVERED,
    CERTIFICATE_RENEWED,
    CERTIFICATE_MATCHED,
    CREDENTIAL_RETRIEVED,
    DEPLOYMENT_JOB_CREATED,
    DEPLOYMENT_DISPATCHED,
    LIVE_ENDPOINT_VERIFIED,
    DISCOVERY_RECONCILED,
    JOB_FAILED,
    JOB_RETRIED
}
