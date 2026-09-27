package com.grassroots.cdm.verification.impl;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.verification.CertificateInspectionService;
import com.grassroots.cdm.verification.VerificationRequest;
import com.grassroots.cdm.verification.VerificationResult;
import com.grassroots.cdm.verification.VerificationService;
import com.grassroots.cdm.verification.VerificationStatus;
import com.grassroots.cdm.verification.model.InspectedCertificate;
import com.grassroots.cdm.verification.model.InspectionOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Production implementation of VerificationService.
 * Validates that live TLS endpoints serve expected certificates, checking thumbprint,
 * serial number, Common Name, Subject Alternative Names (with wildcard support),
 * validity dates, and issuer identity without disabling TLS globally or accepting certificates blindly.
 */
@Service
public class DefaultVerificationService implements VerificationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultVerificationService.class);

    private final CertificateInspectionService inspectionService;
    private final AuditService auditService;

    @Autowired
    public DefaultVerificationService(CertificateInspectionService inspectionService,
                                     @Autowired(required = false) AuditService auditService) {
        this.inspectionService = inspectionService;
        this.auditService = auditService;
    }

    @Override
    public VerificationResult verifyDeployment(DeploymentJob job) {
        if (job == null) {
            throw new IllegalArgumentException("DeploymentJob cannot be null");
        }

        CertificateRecord newCert = job.getNewCertificate();
        if (newCert == null) {
            return VerificationResult.failed(job.getTargetHost(), job.getTargetPort(), null, null,
                    "DeploymentJob has no new certificate assigned for verification");
        }

        String expectedThumbprint = newCert.getFingerprintSha256() != null
                ? newCert.getFingerprintSha256()
                : newCert.getThumbprint();

        VerificationRequest request = VerificationRequest.builder()
                .host(job.getTargetHost())
                .port(job.getTargetPort())
                .expectedThumbprintSha256(expectedThumbprint)
                .expectedSerialNumber(newCert.getSerialNumber())
                .expectedCommonName(newCert.getCommonName())
                .expectedIssuer(newCert.getIssuer())
                .checkValidityDates(true)
                .checkHostname(true)
                .allowWildcard(true)
                .build();

        VerificationResult result = verifyEndpoint(request);

        recordAuditIfEnabled(job.getId() != null ? job.getId().toString() : "UNKNOWN",
                job.getTargetHost(), result);

        return result;
    }

    @Override
    public VerificationResult verifyEndpoint(String host, int port, CertificateRecord expectedCert) {
        if (expectedCert == null) {
            throw new IllegalArgumentException("Expected CertificateRecord cannot be null");
        }

        String expectedThumbprint = expectedCert.getFingerprintSha256() != null
                ? expectedCert.getFingerprintSha256()
                : expectedCert.getThumbprint();

        VerificationRequest request = VerificationRequest.builder()
                .host(host)
                .port(port)
                .expectedThumbprintSha256(expectedThumbprint)
                .expectedSerialNumber(expectedCert.getSerialNumber())
                .expectedCommonName(expectedCert.getCommonName())
                .expectedIssuer(expectedCert.getIssuer())
                .checkValidityDates(true)
                .checkHostname(true)
                .allowWildcard(true)
                .build();

        return verifyEndpoint(request);
    }

    @Override
    public VerificationResult verifyEndpoint(VerificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("VerificationRequest cannot be null");
        }

        String host = request.getHost();
        int port = request.getPort();
        String expectedThumbprint = request.getExpectedThumbprintSha256();

        // 1. Pre-flight validation
        if (host == null || host.isBlank()) {
            return VerificationResult.failed(host, port, null, expectedThumbprint,
                    "Target host cannot be null or blank");
        }
        if (port < 1 || port > 65535) {
            return VerificationResult.failed(host, port, null, expectedThumbprint,
                    "Target port out of valid range: " + port);
        }

        log.info("Initiating TLS endpoint verification on {}:{} expecting thumbprint {}",
                host, port, expectedThumbprint != null ? expectedThumbprint : "[ANY]");

        // 2. Perform live TLS endpoint inspection
        InspectionOutcome outcome = inspectionService.inspectEndpoint(host, port, request.getTimeoutMs());

        // 3. Handle network, connectivity, or protocol errors
        if (!outcome.successful()) {
            if (outcome.failureStatus() == VerificationStatus.UNREACHABLE) {
                log.warn("Target endpoint {}:{} unreachable: {}", host, port, outcome.errorMessage());
                return VerificationResult.unreachable(host, port, expectedThumbprint, outcome.errorMessage());
            } else if (outcome.failureStatus() == VerificationStatus.TLS_ERROR) {
                log.warn("TLS error connecting to {}:{}: {}", host, port, outcome.errorMessage());
                return VerificationResult.tlsError(host, port, expectedThumbprint, outcome.errorMessage());
            }
            return VerificationResult.failed(host, port, null, expectedThumbprint, outcome.errorMessage());
        }

        InspectedCertificate cert = outcome.inspectedCertificate();
        if (cert == null) {
            return VerificationResult.failed(host, port, null, expectedThumbprint,
                    "No certificate presented by endpoint");
        }

        String presentedThumbprint = cert.sha256Fingerprint();

        // 4. Validate certificate temporal validity (not expired, not premature)
        if (request.isCheckValidityDates()) {
            if (cert.isExpired()) {
                String details = "Certificate presented by endpoint is expired: expired at " + cert.notAfter();
                log.warn("Verification failed for {}:{}: {}", host, port, details);
                return VerificationResult.failed(host, port, presentedThumbprint, expectedThumbprint, details, cert);
            }
            if (cert.isNotYetValid()) {
                String details = "Certificate presented by endpoint is not yet valid: valid from " + cert.notBefore();
                log.warn("Verification failed for {}:{}: {}", host, port, details);
                return VerificationResult.failed(host, port, presentedThumbprint, expectedThumbprint, details, cert);
            }
        }

        // 5. Validate Hostname / SAN (with wildcard support)
        if (request.isCheckHostname()) {
            boolean hostnameMatched = cert.matchesHostname(host, request.isAllowWildcard());
            if (!hostnameMatched) {
                String details = "Hostname mismatch: target host '" + host + "' does not match Common Name '"
                        + cert.commonName() + "' or SANs " + cert.subjectAlternativeNames();
                log.warn("Verification failed for {}:{}: {}", host, port, details);
                return VerificationResult.failed(host, port, presentedThumbprint, expectedThumbprint, details, cert);
            }
        }

        // 6. Validate expected thumbprint (SHA-256 or SHA-1)
        if (expectedThumbprint != null && !expectedThumbprint.isBlank()) {
            if (!cert.matchesFingerprint(expectedThumbprint)) {
                String details = "Certificate thumbprint mismatch: presented [" + presentedThumbprint
                        + "] does not match expected [" + expectedThumbprint + "]";
                log.warn("Verification mismatch for {}:{}: {}", host, port, details);
                return VerificationResult.certificateMismatch(host, port, presentedThumbprint, expectedThumbprint, cert, details);
            }
        }

        // 7. Validate expected serial number if provided
        if (request.getExpectedSerialNumber() != null && !request.getExpectedSerialNumber().isBlank()) {
            if (!cert.matchesSerialNumber(request.getExpectedSerialNumber())) {
                String details = "Certificate serial number mismatch: presented [" + cert.serialNumberHex()
                        + " / " + cert.serialNumberDecimal() + "] does not match expected ["
                        + request.getExpectedSerialNumber() + "]";
                log.warn("Verification mismatch for {}:{}: {}", host, port, details);
                return VerificationResult.certificateMismatch(host, port, presentedThumbprint, expectedThumbprint, cert, details);
            }
        }

        // 8. Validate expected Common Name if provided
        if (request.getExpectedCommonName() != null && !request.getExpectedCommonName().isBlank()) {
            if (!request.getExpectedCommonName().equalsIgnoreCase(cert.commonName())) {
                String details = "Common Name mismatch: presented [" + cert.commonName()
                        + "] does not match expected [" + request.getExpectedCommonName() + "]";
                log.warn("Verification mismatch for {}:{}: {}", host, port, details);
                return VerificationResult.certificateMismatch(host, port, presentedThumbprint, expectedThumbprint, cert, details);
            }
        }

        // 9. Validate expected issuer if provided
        if (request.getExpectedIssuer() != null && !request.getExpectedIssuer().isBlank()) {
            if (!cert.matchesIssuer(request.getExpectedIssuer())) {
                String details = "Certificate issuer mismatch: presented [" + cert.issuerDn()
                        + "] does not match expected [" + request.getExpectedIssuer() + "]";
                log.warn("Verification mismatch for {}:{}: {}", host, port, details);
                return VerificationResult.certificateMismatch(host, port, presentedThumbprint, expectedThumbprint, cert, details);
            }
        }

        // 10. All checks passed: endpoint serves the verified certificate
        log.info("Live TLS endpoint {}:{} successfully verified. Active certificate thumbprint: {}",
                host, port, presentedThumbprint);
        return VerificationResult.verified(host, port, presentedThumbprint, expectedThumbprint, cert,
                "Live TLS endpoint verified successfully. Certificate thumbprint, validity dates, and hostname match expected.");
    }

    private void recordAuditIfEnabled(String jobId, String targetHost, VerificationResult result) {
        if (auditService != null) {
            try {
                String outcome = result.verified() ? "SUCCESS" : "FAILED (" + result.status() + ")";
                auditService.recordAudit(AuditEvent.create(
                        AuditAction.LIVE_ENDPOINT_VERIFIED,
                        "DeploymentJob",
                        jobId,
                        "VerificationEngine",
                        outcome,
                        "Verified endpoint " + targetHost + ":" + result.endpointPort() + " - " + result.diagnosticDetails(),
                        targetHost
                ));
            } catch (Exception ex) {
                log.warn("Could not record verification audit event for job {}: {}", jobId, ex.getMessage());
            }
        }
    }
}
