package com.grassroots.cdm.entity.enums;

/**
 * Status of a certificate installed on a specific server/binding.
 */
public enum InstallationStatus {
    INSTALLED,
    PENDING_VERIFICATION,
    VERIFIED,
    FAILED,
    REPLACED,
    ORPHANED
}
