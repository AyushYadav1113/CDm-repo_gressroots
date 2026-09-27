package com.grassroots.cdm.verification;

import com.grassroots.cdm.verification.impl.DefaultCertificateInspectionService;
import com.grassroots.cdm.verification.model.InspectedCertificate;
import com.grassroots.cdm.verification.model.InspectionOutcome;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateInspectionServiceIntegrationTest {

    private DefaultCertificateInspectionService inspectionService;
    private HttpsServer activeHttpsServer;
    private ServerSocket activeFaultyServer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        inspectionService = new DefaultCertificateInspectionService();
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
    @DisplayName("Inspection: Connects to live TLS endpoint and extracts full certificate details")
    void testInspectLiveEndpointSuccess() throws Exception {
        File keystoreFile = tempDir.resolve("standard.p12").toFile();
        TlsTestHelper.createTestKeystore(
                keystoreFile,
                "server",
                "CN=localhost, O=Grassroots CDM, C=US",
                "dns:localhost,ip:127.0.0.1",
                365,
                null
        );

        KeyStore ks = TlsTestHelper.loadKeyStore(keystoreFile);
        activeHttpsServer = TlsTestHelper.startHttpsServer(ks);
        int port = activeHttpsServer.getAddress().getPort();

        InspectionOutcome outcome = inspectionService.inspectEndpoint("localhost", port, 5000);

        assertThat(outcome.successful()).isTrue();
        assertThat(outcome.failureStatus()).isNull();

        InspectedCertificate cert = outcome.inspectedCertificate();
        assertThat(cert).isNotNull();
        assertThat(cert.commonName()).isEqualTo("localhost");
        assertThat(cert.sha256Fingerprint()).hasSize(64);
        assertThat(cert.sha1Fingerprint()).hasSize(40);
        assertThat(cert.serialNumberHex()).isNotBlank();
        assertThat(cert.subjectAlternativeNames()).contains("DNS:localhost", "IP:127.0.0.1");
        assertThat(cert.dnsNames()).contains("localhost");
        assertThat(cert.ipAddresses()).contains("127.0.0.1");
        assertThat(cert.isExpired()).isFalse();
        assertThat(cert.isValidNow()).isTrue();
    }

    @Test
    @DisplayName("Inspection: Multi-SAN certificate extraction")
    void testInspectMultiSanEndpoint() throws Exception {
        File keystoreFile = tempDir.resolve("multisan.p12").toFile();
        TlsTestHelper.createTestKeystore(
                keystoreFile,
                "multi",
                "CN=api.grassroots.internal, O=Grassroots, C=US",
                "dns:api.grassroots.internal,dns:web.grassroots.internal,dns:localhost,ip:127.0.0.1",
                365,
                null
        );

        KeyStore ks = TlsTestHelper.loadKeyStore(keystoreFile);
        activeHttpsServer = TlsTestHelper.startHttpsServer(ks);
        int port = activeHttpsServer.getAddress().getPort();

        InspectionOutcome outcome = inspectionService.inspectEndpoint("localhost", port, 5000);

        assertThat(outcome.successful()).isTrue();
        InspectedCertificate cert = outcome.inspectedCertificate();
        assertThat(cert.commonName()).isEqualTo("api.grassroots.internal");
        assertThat(cert.dnsNames()).contains("api.grassroots.internal", "web.grassroots.internal", "localhost");
        assertThat(cert.ipAddresses()).contains("127.0.0.1");
        assertThat(cert.matchesHostname("localhost", true)).isTrue();
        assertThat(cert.matchesHostname("127.0.0.1", true)).isTrue();
    }

    @Test
    @DisplayName("Inspection: Wildcard certificate extraction")
    void testInspectWildcardEndpoint() throws Exception {
        File keystoreFile = tempDir.resolve("wildcard.p12").toFile();
        TlsTestHelper.createTestKeystore(
                keystoreFile,
                "wildcard",
                "CN=*.grassroots.local, O=Grassroots, C=US",
                "dns:*.grassroots.local,dns:localhost",
                365,
                null
        );

        KeyStore ks = TlsTestHelper.loadKeyStore(keystoreFile);
        activeHttpsServer = TlsTestHelper.startHttpsServer(ks);
        int port = activeHttpsServer.getAddress().getPort();

        InspectionOutcome outcome = inspectionService.inspectEndpoint("localhost", port, 5000);

        assertThat(outcome.successful()).isTrue();
        InspectedCertificate cert = outcome.inspectedCertificate();
        assertThat(cert.commonName()).isEqualTo("*.grassroots.local");
        assertThat(cert.matchesHostname("api.grassroots.local", true)).isTrue();
        assertThat(cert.matchesHostname("web.grassroots.local", true)).isTrue();
        assertThat(cert.matchesHostname("localhost", true)).isTrue();
    }

    @Test
    @DisplayName("Inspection: Detects expired certificate dates")
    void testInspectExpiredCertificate() throws Exception {
        File keystoreFile = tempDir.resolve("expired.p12").toFile();
        TlsTestHelper.createTestKeystore(
                keystoreFile,
                "expired",
                "CN=localhost, O=Grassroots, C=US",
                "dns:localhost,ip:127.0.0.1",
                1,
                "2020/01/01 00:00:00"
        );

        KeyStore ks = TlsTestHelper.loadKeyStore(keystoreFile);
        activeHttpsServer = TlsTestHelper.startHttpsServer(ks);
        int port = activeHttpsServer.getAddress().getPort();

        InspectionOutcome outcome = inspectionService.inspectEndpoint("localhost", port, 5000);

        assertThat(outcome.successful()).isTrue();
        InspectedCertificate cert = outcome.inspectedCertificate();
        assertThat(cert.isExpired()).isTrue();
        assertThat(cert.isValidNow()).isFalse();
    }

    @Test
    @DisplayName("Inspection: Endpoint unavailable (connection refused on closed port) reports UNREACHABLE")
    void testEndpointUnavailableUnreachable() throws Exception {
        // Find an unused closed port by binding and immediately closing a ServerSocket
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }

        InspectionOutcome outcome = inspectionService.inspectEndpoint("localhost", unusedPort, 2000);

        assertThat(outcome.successful()).isFalse();
        assertThat(outcome.failureStatus()).isEqualTo(VerificationStatus.UNREACHABLE);
        assertThat(outcome.errorMessage()).containsIgnoringCase("refused");
    }

    @Test
    @DisplayName("Inspection: Non-TLS server reports TLS_ERROR")
    void testNonTlsServerReportsTlsError() throws Exception {
        activeFaultyServer = TlsTestHelper.startFaultyTlsServer();
        int port = activeFaultyServer.getLocalPort();

        InspectionOutcome outcome = inspectionService.inspectEndpoint("localhost", port, 3000);

        assertThat(outcome.successful()).isFalse();
        assertThat(outcome.failureStatus()).isEqualTo(VerificationStatus.TLS_ERROR);
    }
}
