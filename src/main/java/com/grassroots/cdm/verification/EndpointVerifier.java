package com.grassroots.cdm.verification;

/**
 * Interface contract for live TLS/SSL handshake verification.
 * Connects directly to the target application port (e.g. 443) and extracts
 * the presented X.509 certificate to confirm successful deployment.
 */
public interface EndpointVerifier {

    /**
     * Connects to target host and port to verify that the expected certificate fingerprint is live.
     *
     * @param targetHost Target server FQDN / IP
     * @param targetPort Target TLS port (e.g. 443, 8443)
     * @param expectedFingerprintSha256 Expected SHA-256 fingerprint of the deployed certificate
     * @return VerificationResult indicating whether handshake succeeded with matching certificate
     */
    VerificationResult verifyEndpoint(String targetHost, int targetPort, String expectedFingerprintSha256);
}
