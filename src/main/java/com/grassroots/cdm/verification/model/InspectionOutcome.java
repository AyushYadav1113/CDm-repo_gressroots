package com.grassroots.cdm.verification.model;

import com.grassroots.cdm.verification.VerificationStatus;

/**
 * Outcome of attempting to connect and extract certificate material from a TLS endpoint.
 */
public record InspectionOutcome(
        boolean successful,
        VerificationStatus failureStatus,
        InspectedCertificate inspectedCertificate,
        String errorMessage,
        Throwable cause
) {
    public static InspectionOutcome success(InspectedCertificate cert) {
        return new InspectionOutcome(true, null, cert, null, null);
    }

    public static InspectionOutcome unreachable(String message, Throwable cause) {
        return new InspectionOutcome(false, VerificationStatus.UNREACHABLE, null, message, cause);
    }

    public static InspectionOutcome tlsError(String message, Throwable cause) {
        return new InspectionOutcome(false, VerificationStatus.TLS_ERROR, null, message, cause);
    }
}
