package com.grassroots.cdm.matching;

import com.grassroots.cdm.entity.CertificateRecord;

import java.util.Optional;

/**
 * Result of a certificate matching evaluation between discovered certificates and renewed certificates.
 */
public record MatchResult(
        boolean matched,
        Optional<CertificateRecord> matchedCertificate,
        String matchStrategy,
        double confidenceScore,
        String reason
) {
    public static MatchResult noMatch(String reason) {
        return new MatchResult(false, Optional.empty(), "NONE", 0.0, reason);
    }

    public static MatchResult matched(CertificateRecord cert, String strategy, double confidence, String reason) {
        return new MatchResult(true, Optional.of(cert), strategy, confidence, reason);
    }
}
