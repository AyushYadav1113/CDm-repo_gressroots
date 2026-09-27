package com.grassroots.cdm.verification;

import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;

/**
 * Service contract for verifying that the certificate served by a target TLS endpoint
 * matches the expected certificate and complies with all cryptographic, validity, and hostname criteria.
 */
public interface VerificationService extends EndpointVerifier {

    /**
     * Verifies that the live TLS endpoint serves the certificate expected for the given DeploymentJob.
     *
     * @param job DeploymentJob containing target host, port, and new certificate
     * @return VerificationResult detailing match status and diagnostic reasons
     */
    VerificationResult verifyDeployment(DeploymentJob job);

    /**
     * Verifies that the live TLS endpoint serves the expected CertificateRecord.
     *
     * @param host Target endpoint host or IP
     * @param port Target endpoint port (e.g. 443, 8443)
     * @param expectedCert Expected CertificateRecord metadata
     * @return VerificationResult detailing match status and diagnostic reasons
     */
    VerificationResult verifyEndpoint(String host, int port, CertificateRecord expectedCert);

    /**
     * Executes verification according to custom VerificationRequest criteria.
     *
     * @param request Verification criteria and target endpoint
     * @return VerificationResult detailing match status and diagnostic reasons
     */
    VerificationResult verifyEndpoint(VerificationRequest request);

    /**
     * Backwards-compatible EndpointVerifier contract method.
     */
    @Override
    default VerificationResult verifyEndpoint(String targetHost, int targetPort, String expectedFingerprintSha256) {
        return verifyEndpoint(VerificationRequest.builder()
                .host(targetHost)
                .port(targetPort)
                .expectedThumbprintSha256(expectedFingerprintSha256)
                .checkValidityDates(true)
                .checkHostname(false) // thumbprint-only mode
                .build());
    }
}
