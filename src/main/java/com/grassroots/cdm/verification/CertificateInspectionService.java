package com.grassroots.cdm.verification;

import com.grassroots.cdm.verification.model.InspectedCertificate;
import com.grassroots.cdm.verification.model.InspectionOutcome;

import java.security.cert.X509Certificate;

/**
 * Service contract for connecting to live TLS endpoints and parsing presented X.509 certificate material.
 */
public interface CertificateInspectionService {

    /**
     * Connects to a target host and port over TLS to inspect the active certificate.
     *
     * @param host Target host or IP address
     * @param port Target port (e.g. 443, 8443)
     * @param timeoutMs Connection and read timeout in milliseconds
     * @return InspectionOutcome containing the inspected certificate or failure details
     */
    InspectionOutcome inspectEndpoint(String host, int port, int timeoutMs);

    /**
     * Connects to a target host and port over TLS with default 5-second timeout.
     *
     * @param host Target host or IP address
     * @param port Target port
     * @return InspectionOutcome containing the inspected certificate or failure details
     */
    default InspectionOutcome inspectEndpoint(String host, int port) {
        return inspectEndpoint(host, port, 5000);
    }

    /**
     * Parses an X509Certificate into a structured InspectedCertificate domain model.
     *
     * @param cert The active X.509 certificate
     * @return InspectedCertificate with extracted metadata
     */
    InspectedCertificate parseCertificate(X509Certificate cert);

    /**
     * Parses an X509Certificate and optional chain into a structured InspectedCertificate domain model.
     *
     * @param cert The primary active X.509 certificate
     * @param chain Full certificate chain presented by peer
     * @return InspectedCertificate with extracted metadata
     */
    InspectedCertificate parseCertificate(X509Certificate cert, X509Certificate[] chain);
}
