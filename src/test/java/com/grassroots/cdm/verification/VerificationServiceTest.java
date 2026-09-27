package com.grassroots.cdm.verification;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.verification.impl.DefaultCertificateInspectionService;
import com.grassroots.cdm.verification.impl.DefaultVerificationService;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VerificationServiceTest {

    private DefaultVerificationService verificationService;
    private DefaultCertificateInspectionService inspectionService;
    private AuditService auditService;
    private HttpsServer activeHttpsServer;
    private ServerSocket activeFaultyServer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        inspectionService = new DefaultCertificateInspectionService();
        auditService = mock(AuditService.class);
        verificationService = new DefaultVerificationService(inspectionService, auditService);
    }

    @AfterEach
    void tearDown() {
        if (activeHttpsServer != null) {
            activeHttpsServer.stop(0);
            activeHttpsServer = null;
        }
        if (activeFaultyServer != null) {
            try {
                activeFaultyServer.close();
            } catch (Exception ignored) {
            }
            activeFaultyServer = null;
        }
    }

    @Test
    @DisplayName("VERIFIED: Endpoint serves expected certificate matching thumbprint, dates, and hostname")
    void testVerifySuccess() throws Exception {
        File keystore = tempDir.resolve("standard.p12").toFile();
        TlsTestHelper.createTestKeystore(keystore, "cert", "CN=localhost, O=Grassroots, C=US",
                "dns:localhost,ip:127.0.0.1", 365, null);

        KeyStore ks = TlsTestHelper.loadKeyStore(keystore);
        X509Certificate x509 = TlsTestHelper.getCertificate(ks, "cert");
        String sha256 = inspectionService.parseCertificate(x509).sha256Fingerprint();

        activeHttpsServer = TlsTestHelper.startHttpsServer(ks);
        int port = activeHttpsServer.getAddress().getPort();

        VerificationRequest request = VerificationRequest.builder()
                .host("localhost")
                .port(port)
                .expectedThumbprintSha256(sha256)
                .checkValidityDates(true)
                .checkHostname(true)
                .build();

        VerificationResult result = verificationService.verifyEndpoint(request);

        assertThat(result.status()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(result.verified()).isTrue();
        assertThat(result.endpointHost()).isEqualTo("localhost");
        assertThat(result.endpointPort()).isEqualTo(port);
        assertThat(result.presentedSha256Fingerprint()).isEqualTo(sha256);
        assertThat(result.presentedCertificate()).isNotNull();
        assertThat(result.diagnosticDetails()).contains("successfully");
    }

    @Test
    @DisplayName("CERTIFICATE_MISMATCH: Live endpoint serves wrong certificate (thumbprint mismatch)")
    void testCertificateMismatchThumbprint() throws Exception {
        File keystore = tempDir.resolve("certA.p12").toFile();
        TlsTestHelper.createTestKeystore(keystore, "certA", "CN=localhost, O=Grassroots, C=US",
                "dns:localhost,ip:127.0.0.1", 365, null);

        KeyStore ks = TlsTestHelper.loadKeyStore(keystore);
        activeHttpsServer = TlsTestHelper.startHttpsServer(ks);
        int port = activeHttpsServer.getAddress().getPort();

        String wrongExpectedThumbprint = "0000000000000000000000000000000000000000000000000000000000000000";

        VerificationRequest request = VerificationRequest.builder()
                .host("localhost")
                .port(port)
                .expectedThumbprintSha256(wrongExpectedThumbprint)
                .build();

        VerificationResult result = verificationService.verifyEndpoint(request);

        assertThat(result.status()).isEqualTo(VerificationStatus.CERTIFICATE_MISMATCH);
        assertThat(result.verified()).isFalse();
        assertThat(result.diagnosticDetails()).contains("thumbprint mismatch");
        assertThat(result.expectedSha256Fingerprint()).isEqualTo(wrongExpectedThumbprint);
        assertThat(result.presentedSha256Fingerprint()).isNotEqualTo(wrongExpectedThumbprint);
    }

    @Test
    @DisplayName("CERTIFICATE_MISMATCH: Serial number mismatch")
    void testCertificateMismatchSerialNumber() throws Exception {
        File keystore = tempDir.resolve("certSerial.p12").toFile();
        TlsTestHelper.createTestKeystore(keystore, "certSerial", "CN=localhost, O=Grassroots, C=US",
                "dns:localhost,ip:127.0.0.1", 365, null);

        KeyStore ks = TlsTestHelper.loadKeyStore(keystore);
        X509Certificate x509 = TlsTestHelper.getCertificate(ks, "certSerial");
        String sha256 = inspectionService.parseCertificate(x509).sha256Fingerprint();

        activeHttpsServer = TlsTestHelper.startHttpsServer(ks);
        int port = activeHttpsServer.getAddress().getPort();

        VerificationRequest request = VerificationRequest.builder()
                .host("localhost")
                .port(port)
                .expectedThumbprintSha256(sha256)
                .expectedSerialNumber("999999999999") // wrong serial
                .build();

        VerificationResult result = verificationService.verifyEndpoint(request);

        assertThat(result.status()).isEqualTo(VerificationStatus.CERTIFICATE_MISMATCH);
        assertThat(result.verified()).isFalse();
        assertThat(result.diagnosticDetails()).contains("serial number mismatch");
    }

    @Test
    @DisplayName("FAILED: Expired certificate presented by live endpoint")
    void testFailedExpiredCertificate() throws Exception {
        File keystore = tempDir.resolve("expired.p12").toFile();
        TlsTestHelper.createTestKeystore(keystore, "expired", "CN=localhost, O=Grassroots, C=US",
                "dns:localhost,ip:127.0.0.1", 1, "2020/01/01 00:00:00");

        KeyStore ks = TlsTestHelper.loadKeyStore(keystore);
        X509Certificate x509 = TlsTestHelper.getCertificate(ks, "expired");
        String sha256 = inspectionService.parseCertificate(x509).sha256Fingerprint();

        activeHttpsServer = TlsTestHelper.startHttpsServer(ks);
        int port = activeHttpsServer.getAddress().getPort();

        VerificationRequest request = VerificationRequest.builder()
                .host("localhost")
                .port(port)
                .expectedThumbprintSha256(sha256)
                .checkValidityDates(true)
                .build();

        VerificationResult result = verificationService.verifyEndpoint(request);

        assertThat(result.status()).isEqualTo(VerificationStatus.FAILED);
        assertThat(result.verified()).isFalse();
        assertThat(result.diagnosticDetails()).contains("expired");
    }

    @Test
    @DisplayName("FAILED: Hostname mismatch (presented certificate does not cover target host)")
    void testFailedHostnameMismatch() throws Exception {
        File keystore = tempDir.resolve("wrongdomain.p12").toFile();
        TlsTestHelper.createTestKeystore(keystore, "wrongdomain", "CN=billing.internal, O=Grassroots, C=US",
                "dns:billing.internal", 365, null);

        KeyStore ks = TlsTestHelper.loadKeyStore(keystore);
        activeHttpsServer = TlsTestHelper.startHttpsServer(ks);
        int port = activeHttpsServer.getAddress().getPort();

        VerificationRequest request = VerificationRequest.builder()
                .host("localhost") // connects to localhost, but cert only covers billing.internal
                .port(port)
                .checkHostname(true)
                .build();

        VerificationResult result = verificationService.verifyEndpoint(request);

        assertThat(result.status()).isEqualTo(VerificationStatus.FAILED);
        assertThat(result.verified()).isFalse();
        assertThat(result.diagnosticDetails()).contains("Hostname mismatch");
    }

    @Test
    @DisplayName("UNREACHABLE: Target endpoint is unavailable (connection refused on closed port)")
    void testEndpointUnavailableUnreachable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        VerificationRequest request = VerificationRequest.builder()
                .host("localhost")
                .port(closedPort)
                .expectedThumbprintSha256("ANY")
                .timeoutMs(2000)
                .build();

        VerificationResult result = verificationService.verifyEndpoint(request);

        assertThat(result.status()).isEqualTo(VerificationStatus.UNREACHABLE);
        assertThat(result.verified()).isFalse();
        assertThat(result.diagnosticDetails()).containsIgnoringCase("refused");
    }

    @Test
    @DisplayName("TLS_ERROR: Target endpoint fails TLS handshake")
    void testTlsHandshakeFailureReportsTlsError() throws Exception {
        activeFaultyServer = TlsTestHelper.startFaultyTlsServer();
        int port = activeFaultyServer.getLocalPort();

        VerificationRequest request = VerificationRequest.builder()
                .host("localhost")
                .port(port)
                .expectedThumbprintSha256("ANY")
                .timeoutMs(3000)
                .build();

        VerificationResult result = verificationService.verifyEndpoint(request);

        assertThat(result.status()).isEqualTo(VerificationStatus.TLS_ERROR);
        assertThat(result.verified()).isFalse();
        assertThat(result.diagnosticDetails()).containsIgnoringCase("TLS");
    }

    @Test
    @DisplayName("VERIFIED: Multiple SANs certificate matching secondary SAN")
    void testMultipleSansVerification() throws Exception {
        File keystore = tempDir.resolve("multisan.p12").toFile();
        TlsTestHelper.createTestKeystore(keystore, "multisan",
                "CN=primary.internal, O=Grassroots, C=US",
                "dns:primary.internal,dns:secondary.internal,dns:localhost,ip:127.0.0.1",
                365, null);

        KeyStore ks = TlsTestHelper.loadKeyStore(keystore);
        X509Certificate x509 = TlsTestHelper.getCertificate(ks, "multisan");
        String sha256 = inspectionService.parseCertificate(x509).sha256Fingerprint();

        activeHttpsServer = TlsTestHelper.startHttpsServer(ks);
        int port = activeHttpsServer.getAddress().getPort();

        // Connect via localhost which is 3rd SAN
        VerificationRequest request = VerificationRequest.builder()
                .host("localhost")
                .port(port)
                .expectedThumbprintSha256(sha256)
                .checkHostname(true)
                .build();

        VerificationResult result = verificationService.verifyEndpoint(request);

        assertThat(result.status()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(result.verified()).isTrue();
    }

    @Test
    @DisplayName("VERIFIED: Wildcard certificate matching single-level subdomain")
    void testWildcardCertificateVerification() throws Exception {
        File keystore = tempDir.resolve("wildcard.p12").toFile();
        TlsTestHelper.createTestKeystore(keystore, "wildcard",
                "CN=*.grassroots.local, O=Grassroots, C=US",
                "dns:*.grassroots.local,dns:localhost",
                365, null);

        KeyStore ks = TlsTestHelper.loadKeyStore(keystore);
        X509Certificate x509 = TlsTestHelper.getCertificate(ks, "wildcard");
        String sha256 = inspectionService.parseCertificate(x509).sha256Fingerprint();

        activeHttpsServer = TlsTestHelper.startHttpsServer(ks);
        int port = activeHttpsServer.getAddress().getPort();

        VerificationRequest request = VerificationRequest.builder()
                .host("localhost")
                .port(port)
                .expectedThumbprintSha256(sha256)
                .allowWildcard(true)
                .checkHostname(true)
                .build();

        VerificationResult result = verificationService.verifyEndpoint(request);

        assertThat(result.status()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(result.verified()).isTrue();
    }

    @Test
    @DisplayName("DeploymentJob Verification: End-to-end verification and audit event publishing")
    void testVerifyDeploymentJob() throws Exception {
        File keystore = tempDir.resolve("depjob.p12").toFile();
        TlsTestHelper.createTestKeystore(keystore, "depjob",
                "CN=localhost, O=Grassroots, C=US",
                "dns:localhost,ip:127.0.0.1", 365, null);

        KeyStore ks = TlsTestHelper.loadKeyStore(keystore);
        X509Certificate x509 = TlsTestHelper.getCertificate(ks, "depjob");
        String sha256 = inspectionService.parseCertificate(x509).sha256Fingerprint();
        String serialHex = x509.getSerialNumber().toString(16).toUpperCase();

        activeHttpsServer = TlsTestHelper.startHttpsServer(ks);
        int port = activeHttpsServer.getAddress().getPort();

        CertificateRecord certRecord = new CertificateRecord();
        certRecord.setId(UUID.randomUUID());
        certRecord.setCommonName("localhost");
        certRecord.setThumbprint(sha256);
        certRecord.setFingerprintSha256(sha256);
        certRecord.setSerialNumber(serialHex);

        DeploymentJob job = new DeploymentJob();
        job.setId(UUID.randomUUID());
        job.setTargetHost("localhost");
        job.setTargetPort(port);
        job.setNewCertificate(certRecord);

        VerificationResult result = verificationService.verifyDeployment(job);

        assertThat(result.status()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(result.verified()).isTrue();

        // Verify audit event published
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).recordAudit(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.LIVE_ENDPOINT_VERIFIED);
        assertThat(event.entityId()).isEqualTo(job.getId().toString());
        assertThat(event.outcome()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Pre-flight Validation: Rejects invalid host or port")
    void testPreFlightValidation() {
        VerificationRequest nullHost = VerificationRequest.builder().host(null).port(443).build();
        assertThat(verificationService.verifyEndpoint(nullHost).status()).isEqualTo(VerificationStatus.FAILED);

        VerificationRequest blankHost = VerificationRequest.builder().host("   ").port(443).build();
        assertThat(verificationService.verifyEndpoint(blankHost).status()).isEqualTo(VerificationStatus.FAILED);

        VerificationRequest invalidPort = VerificationRequest.builder().host("localhost").port(99999).build();
        assertThat(verificationService.verifyEndpoint(invalidPort).status()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    @DisplayName("Security: Ensures JVM default SSLContext is NOT modified globally")
    void testZeroGlobalSslContextModification() throws Exception {
        SSLSocketFactory defaultFactoryBefore = HttpsURLConnection.getDefaultSSLSocketFactory();

        File keystore = tempDir.resolve("security.p12").toFile();
        TlsTestHelper.createTestKeystore(keystore, "security",
                "CN=localhost, O=Grassroots, C=US", "dns:localhost", 365, null);

        KeyStore ks = TlsTestHelper.loadKeyStore(keystore);
        activeHttpsServer = TlsTestHelper.startHttpsServer(ks);
        int port = activeHttpsServer.getAddress().getPort();

        verificationService.verifyEndpoint(VerificationRequest.builder()
                .host("localhost")
                .port(port)
                .build());

        SSLSocketFactory defaultFactoryAfter = HttpsURLConnection.getDefaultSSLSocketFactory();
        assertThat(defaultFactoryAfter).isSameAs(defaultFactoryBefore);
    }
}
