package com.grassroots.cdm.verification;

import com.grassroots.cdm.verification.model.InspectedCertificate;

import java.time.Instant;

/**
 * Result of live endpoint TLS handshake verification.
 */
public record VerificationResult(
        VerificationStatus status,
        boolean verified,
        String endpointHost,
        int endpointPort,
        String presentedSha256Fingerprint,
        String expectedSha256Fingerprint,
        InspectedCertificate presentedCertificate,
        Instant verifiedAt,
        String diagnosticDetails
) {
    /**
     * Backwards-compatible constructor for existing invocations.
     */
    public VerificationResult(boolean verified, String endpointHost, int endpointPort,
                              String presentedSha256Fingerprint, String expectedSha256Fingerprint,
                              Instant verifiedAt, String diagnosticDetails) {
        this(verified ? VerificationStatus.VERIFIED : VerificationStatus.FAILED,
                verified, endpointHost, endpointPort, presentedSha256Fingerprint,
                expectedSha256Fingerprint, null, verifiedAt, diagnosticDetails);
    }

    public static VerificationResult verified(String host, int port, String presented, String expected,
                                             InspectedCertificate cert, String details) {
        return new VerificationResult(VerificationStatus.VERIFIED, true, host, port,
                presented, expected, cert, Instant.now(), details);
    }

    public static VerificationResult successful(String host, int port, String fingerprint, String details) {
        return new VerificationResult(VerificationStatus.VERIFIED, true, host, port,
                fingerprint, fingerprint, null, Instant.now(), details);
    }

    public static VerificationResult failed(String host, int port, String presented, String expected, String details) {
        return new VerificationResult(VerificationStatus.FAILED, false, host, port,
                presented, expected, null, Instant.now(), details);
    }

    public static VerificationResult failed(String host, int port, String presented, String expected,
                                           String details, InspectedCertificate cert) {
        return new VerificationResult(VerificationStatus.FAILED, false, host, port,
                presented, expected, cert, Instant.now(), details);
    }

    public static VerificationResult unreachable(String host, int port, String expected, String details) {
        return new VerificationResult(VerificationStatus.UNREACHABLE, false, host, port,
                null, expected, null, Instant.now(), details);
    }

    public static VerificationResult certificateMismatch(String host, int port, String presented, String expected,
                                                        InspectedCertificate cert, String details) {
        return new VerificationResult(VerificationStatus.CERTIFICATE_MISMATCH, false, host, port,
                presented, expected, cert, Instant.now(), details);
    }

    public static VerificationResult tlsError(String host, int port, String expected, String details) {
        return new VerificationResult(VerificationStatus.TLS_ERROR, false, host, port,
                null, expected, null, Instant.now(), details);
    }
}
