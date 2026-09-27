package com.grassroots.cdm.verification;

/**
 * Standardized status outcomes for live endpoint TLS certificate verification.
 */
public enum VerificationStatus {
    /**
     * Endpoint TLS connection succeeded and the presented certificate
     * matches all expected criteria (thumbprint, serial, validity dates, hostname/SAN).
     */
    VERIFIED,

    /**
     * General verification failure (e.g. certificate is expired, not yet valid,
     * or hostname does not match Common Name or Subject Alternative Names).
     */
    FAILED,

    /**
     * Target host/port could not be reached (connection refused, unknown host,
     * connection timeout, socket unreachable).
     */
    UNREACHABLE,

    /**
     * Endpoint is reachable and completed TLS handshake, but the presented
     * certificate does not match the expected certificate (thumbprint, serial, or issuer mismatch).
     */
    CERTIFICATE_MISMATCH,

    /**
     * TLS protocol error, cipher suite mismatch, handshake failure,
     * or SSL protocol negotiation failure.
     */
    TLS_ERROR
}
