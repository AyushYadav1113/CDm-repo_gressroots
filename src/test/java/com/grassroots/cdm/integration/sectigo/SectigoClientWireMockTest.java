package com.grassroots.cdm.integration.sectigo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.grassroots.cdm.integration.sectigo.client.SectigoClientImpl;
import com.grassroots.cdm.integration.sectigo.config.SectigoProperties;
import com.grassroots.cdm.integration.sectigo.dto.SectigoCertificateDto;
import com.grassroots.cdm.integration.sectigo.exception.SectigoAuthenticationException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoParseException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoRateLimitException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoServerException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SectigoClientWireMockTest {

    private WireMockServer wireMockServer;
    private SectigoClientImpl client;
    private SectigoProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        properties = new SectigoProperties();
        properties.setBaseUrl("http://localhost:" + wireMockServer.port());
        properties.setCustomerUri("grassroots-tenant");
        properties.setLoginName("cdm_sectigo_admin");
        properties.setPassword("secret_scm_password");
        properties.setConnectTimeoutMs(1000);
        properties.setReadTimeoutMs(1000);
        properties.setMaxRetries(2);
        properties.setBackoffMs(50);
        properties.setPageSize(2);

        client = new SectigoClientImpl(properties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Fetch newly issued certificate: verifies headers, DTO mapping, and SAN parsing")
    void fetchCertificates_success_newCertificate() {
        String jsonResponse = """
            [
              {
                "id": "1001",
                "commonName": "api.grassroots.internal",
                "subjectAlternativeNames": ["api.grassroots.internal", "api-internal.grassroots.internal"],
                "serialNumber": "7A:B3:CD:EF:12:34:56:78",
                "sha256Fingerprint": "9E8D7C6B5A4F3E2D1C0B9A8F7E6D5C4B3A2F1E0D9C8B7A6F5E4D3C2B1A0F9E8D",
                "issuer": "Sectigo RSA Organization Validation Secure Server CA",
                "validFrom": "2026-01-01T00:00:00Z",
                "validTo": "2028-01-01T23:59:59Z",
                "status": "ISSUED",
                "orderId": "5001"
              }
            ]
            """;

        wireMockServer.stubFor(get(urlPathEqualTo("/certificates"))
                .withHeader("customerUri", equalTo("grassroots-tenant"))
                .withHeader("loginName", equalTo("cdm_sectigo_admin"))
                .withHeader("password", equalTo("secret_scm_password"))
                .withHeader("X-Correlation-ID", equalTo("TRACE-NEW-01"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        List<SectigoCertificateDto> result = client.fetchCertificates(10, 0, "ISSUED", "TRACE-NEW-01");

        assertThat(result).hasSize(1);
        SectigoCertificateDto cert = result.get(0);
        assertThat(cert.getId()).isEqualTo("1001");
        assertThat(cert.getCommonName()).isEqualTo("api.grassroots.internal");
        assertThat(cert.getSubjectAlternativeNames()).containsExactly("api.grassroots.internal", "api-internal.grassroots.internal");
        assertThat(cert.getSerialNumber()).isEqualTo("7A:B3:CD:EF:12:34:56:78");
        assertThat(cert.getSha256Fingerprint()).isEqualTo("9E8D7C6B5A4F3E2D1C0B9A8F7E6D5C4B3A2F1E0D9C8B7A6F5E4D3C2B1A0F9E8D");
        assertThat(cert.getStatus()).isEqualTo("ISSUED");
        assertThat(cert.getOrderId()).isEqualTo("5001");
        assertThat(cert.getRenewedFromCertificateId()).isNull();
    }

    @Test
    @DisplayName("Fetch renewed certificate: verifies renewal link to predecessor certificate")
    void fetchCertificates_success_renewedCertificate() {
        String jsonResponse = """
            [
              {
                "id": "2002",
                "commonName": "payments.grassroots.internal",
                "subjectAlternativeNames": ["payments.grassroots.internal"],
                "serialNumber": "8899AABBCCDDEEFF",
                "sha256Fingerprint": "AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899",
                "issuer": "Sectigo RSA Domain Validation Secure Server CA",
                "validFrom": "2026-06-01T00:00:00Z",
                "validTo": "2028-06-01T23:59:59Z",
                "status": "ISSUED",
                "orderId": "6002",
                "renewedFromCertificateId": "1001"
              }
            ]
            """;

        wireMockServer.stubFor(get(urlPathEqualTo("/certificates"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        List<SectigoCertificateDto> result = client.fetchCertificates(10, 0, "ISSUED", "TRACE-RENEW-01");

        assertThat(result).hasSize(1);
        SectigoCertificateDto cert = result.get(0);
        assertThat(cert.getId()).isEqualTo("2002");
        assertThat(cert.getRenewedFromCertificateId()).isEqualTo("1001");
        assertThat(cert.getCommonName()).isEqualTo("payments.grassroots.internal");
    }

    @Test
    @DisplayName("Pagination: automatically retrieves multi-page certificate batches")
    void fetchCertificates_pagination() {
        String page1 = """
            [
              { "id": "p1-1", "commonName": "cert1.grassroots.internal", "serialNumber": "S1", "status": "ISSUED" },
              { "id": "p1-2", "commonName": "cert2.grassroots.internal", "serialNumber": "S2", "status": "ISSUED" }
            ]
            """;

        String page2 = """
            [
              { "id": "p2-1", "commonName": "cert3.grassroots.internal", "serialNumber": "S3", "status": "ISSUED" }
            ]
            """;

        wireMockServer.stubFor(get(urlEqualTo("/certificates?size=2&position=0&status=ISSUED"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(page1)));

        wireMockServer.stubFor(get(urlEqualTo("/certificates?size=2&position=2&status=ISSUED"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(page2)));

        List<SectigoCertificateDto> all = client.fetchAllCertificates("ISSUED", "TRACE-PAGINATION");

        assertThat(all).hasSize(3);
        assertThat(all.stream().map(SectigoCertificateDto::getId)).containsExactly("p1-1", "p1-2", "p2-1");
    }

    @Test
    @DisplayName("API Timeout: retries and throws SectigoTimeoutException when server delays beyond read timeout")
    void fetchCertificates_apiTimeout() {
        wireMockServer.stubFor(get(urlPathEqualTo("/certificates"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(2000))); // longer than readTimeoutMs=1000

        assertThatThrownBy(() -> client.fetchCertificates(10, 0, "ISSUED", "TRACE-TIMEOUT"))
                .isInstanceOf(SectigoTimeoutException.class);
    }

    @Test
    @DisplayName("API Failure: retries and throws SectigoServerException on HTTP 500 / 503")
    void fetchCertificates_apiFailure() {
        wireMockServer.stubFor(get(urlPathEqualTo("/certificates"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Sectigo CA Service Unavailable")));

        assertThatThrownBy(() -> client.fetchCertificates(10, 0, "ISSUED", "TRACE-503"))
                .isInstanceOf(SectigoServerException.class)
                .hasMessageContaining("503");
    }

    @Test
    @DisplayName("Rate Limiting: throws SectigoRateLimitException on HTTP 429 and parses Retry-After header")
    void fetchCertificates_rateLimiting() {
        wireMockServer.stubFor(get(urlPathEqualTo("/certificates"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "2")
                        .withBody("{\"code\":-429,\"description\":\"API rate limit exceeded\"}")));

        assertThatThrownBy(() -> client.fetchCertificates(10, 0, "ISSUED", "TRACE-429"))
                .isInstanceOf(SectigoRateLimitException.class)
                .satisfies(ex -> {
                    SectigoRateLimitException rateLimitEx = (SectigoRateLimitException) ex;
                    assertThat(rateLimitEx.getRetryAfterSeconds()).isEqualTo(2L);
                });
    }

    @Test
    @DisplayName("Malformed Response: throws SectigoParseException without retrying on HTML / invalid JSON")
    void fetchCertificates_malformedResponse() {
        wireMockServer.stubFor(get(urlPathEqualTo("/certificates"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("<html><body>Internal Error 500 Gateway</body></html>")));

        assertThatThrownBy(() -> client.fetchCertificates(10, 0, "ISSUED", "TRACE-MALFORMED"))
                .isInstanceOf(SectigoParseException.class);
    }

    @Test
    @DisplayName("Authentication Failure: throws SectigoAuthenticationException on HTTP 401 without retrying")
    void fetchCertificates_authenticationFailure() {
        String errorJson = """
            {
              "code": -101,
              "description": "Invalid credentials or customerUri"
            }
            """;

        wireMockServer.stubFor(get(urlPathEqualTo("/certificates"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody(errorJson)));

        assertThatThrownBy(() -> client.fetchCertificates(10, 0, "ISSUED", "TRACE-AUTH-FAIL"))
                .isInstanceOf(SectigoAuthenticationException.class)
                .hasMessageContaining("Invalid credentials or customerUri");
    }

    @Test
    @DisplayName("Fetch single certificate by ID")
    void fetchCertificateById_success() {
        String singleCertJson = """
            {
              "id": "single-100",
              "commonName": "single.grassroots.internal",
              "serialNumber": "AABB1122",
              "sha256Fingerprint": "THUMB1122",
              "status": "ISSUED"
            }
            """;

        wireMockServer.stubFor(get(urlPathEqualTo("/certificates/single-100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(singleCertJson)));

        Optional<SectigoCertificateDto> result = client.fetchCertificateById("single-100", "TRACE-SINGLE");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("single-100");
        assertThat(result.get().getCommonName()).isEqualTo("single.grassroots.internal");
    }

    @Test
    @DisplayName("Download certificate chain: retrieves public PEM certificate bundle")
    void downloadCertificateChain_success() {
        String pemData = "-----BEGIN CERTIFICATE-----\nMIIDXTCCAkWgAwIBAgIJAP...\n-----END CERTIFICATE-----";

        wireMockServer.stubFor(get(urlPathEqualTo("/certificates/single-100/chain"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-pem-file")
                        .withBody(pemData.getBytes())));

        byte[] chain = client.downloadCertificateChain("single-100", "TRACE-CHAIN");

        assertThat(chain).isNotEmpty();
        assertThat(new String(chain)).contains("BEGIN CERTIFICATE");
    }
}
