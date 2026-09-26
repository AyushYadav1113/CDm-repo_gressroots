package com.grassroots.cdm.verification;

import java.time.Instant;

/**
 * Result of live endpoint TLS handshake verification.
 */
public record VerificationResult(
        boolean verified,
        String endpointHost,
        int endpointPort,
        String presentedSha256Fingerprint,
        String expectedSha256Fingerprint,
        Instant verifiedAt,
        String diagnosticDetails
) {
    public static VerificationResult successful(String host, int port, String fingerprint, String details) {
        return new VerificationResult(true, host, port, fingerprint, fingerprint, Instant.now(), details);
    }

    public static VerificationResult failed(String host, int port, String presented, String expected, String details) {
        return new VerificationResult(false, host, port, presented, expected, Instant.now(), details);
    }
}
