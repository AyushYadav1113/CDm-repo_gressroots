package com.grassroots.cdm.integration.servicenow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.grassroots.cdm.integration.servicenow.client.ServiceNowClientImpl;
import com.grassroots.cdm.integration.servicenow.config.ServiceNowProperties;
import com.grassroots.cdm.integration.servicenow.dto.ServiceNowCertificateDto;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowAuthenticationException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowParseException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowServerException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceNowClientWireMockTest {

    private WireMockServer wireMockServer;
    private ServiceNowClientImpl client;
    private ServiceNowProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        properties = new ServiceNowProperties();
        properties.setBaseUrl("http://localhost:" + wireMockServer.port());
        properties.setTable("cmdb_ci_certificate");
        properties.setUsername("cdm_user");
        properties.setPassword("secret_token");
        properties.setConnectTimeoutMs(1000);
        properties.setReadTimeoutMs(1000);
        properties.setMaxRetries(2);
        properties.setBackoffMs(50);

        client = new ServiceNowClientImpl(properties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Successful discovery of multiple certificates via Table API")
    void fetchCertificates_success_multiple() {
        String jsonResponse = """
            {
              "result": [
                {
                  "sys_id": "sys-cert-001",
                  "common_name": "api.grassroots.internal",
                  "serial_number": "1234567890ABCDEF",
                  "thumbprint": "8F3A2B1C4D5E6F70",
                  "issuer": "CN=Grassroots Root CA",
                  "valid_from": "2024-01-01 00:00:00",
                  "valid_to": "2026-01-01 23:59:59",
                  "subject_alternative_names": "api.grassroots.internal, internal.grassroots.com",
                  "target_host": "api-server-01.grassroots.internal",
                  "port": "443",
                  "technology": "APACHE"
                },
                {
                  "sys_id": "sys-cert-002",
                  "common_name": "portal.grassroots.internal",
                  "serial_number": "9876543210FEDCBA",
                  "thumbprint": "A1B2C3D4E5F60718",
                  "issuer": "CN=Grassroots Root CA",
                  "valid_from": "2024-06-01 00:00:00",
                  "valid_to": "2025-06-01 23:59:59",
                  "subject_alternative_names": "portal.grassroots.internal",
                  "target_host": "web-server-02.grassroots.internal",
                  "port": "8443",
                  "technology": "IIS"
                }
              ]
            }
            """;

        wireMockServer.stubFor(get(urlPathEqualTo("/api/now/table/cmdb_ci_certificate"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        List<ServiceNowCertificateDto> result = client.fetchCertificates("active=true", 10, 0, "TRACE-001");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSysId()).isEqualTo("sys-cert-001");
        assertThat(result.get(0).getCommonName()).isEqualTo("api.grassroots.internal");
        assertThat(result.get(0).getThumbprint()).isEqualTo("8F3A2B1C4D5E6F70");
        assertThat(result.get(0).getTargetHost()).isEqualTo("api-server-01.grassroots.internal");

        assertThat(result.get(1).getSysId()).isEqualTo("sys-cert-002");
        assertThat(result.get(1).getCommonName()).isEqualTo("portal.grassroots.internal");
        assertThat(result.get(1).getPort()).isEqualTo("8443");
    }

    @Test
    @DisplayName("Fetch certificate by sys_id successfully")
    void fetchCertificateBySysId_success() {
        String jsonResponse = """
            {
              "result": {
                "sys_id": "sys-cert-single",
                "common_name": "secure.grassroots.internal",
                "serial_number": "55667788",
                "thumbprint": "EEFFAABB11223344",
                "issuer": "CN=Grassroots Intermediate CA",
                "valid_to": "2025-12-31 23:59:59"
              }
            }
            """;

        wireMockServer.stubFor(get(urlPathEqualTo("/api/now/table/cmdb_ci_certificate/sys-cert-single"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        Optional<ServiceNowCertificateDto> certOpt = client.fetchCertificateBySysId("sys-cert-single", "TRACE-002");

        assertThat(certOpt).isPresent();
        assertThat(certOpt.get().getSysId()).isEqualTo("sys-cert-single");
        assertThat(certOpt.get().getCommonName()).isEqualTo("secure.grassroots.internal");
    }

    @Test
    @DisplayName("Handle HTTP 401 Authentication Failure")
    void fetchCertificates_authenticationFailure() {
        String errorJson = """
            {
              "error": {
                "message": "User Not Authenticated",
                "detail": "Required to provide Auth information"
              },
              "status": "failure"
            }
            """;

        wireMockServer.stubFor(get(urlPathEqualTo("/api/now/table/cmdb_ci_certificate"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody(errorJson)));

        assertThatThrownBy(() -> client.fetchCertificates("active=true", 10, 0, "TRACE-AUTH-FAIL"))
                .isInstanceOf(ServiceNowAuthenticationException.class)
                .hasMessageContaining("User Not Authenticated");
    }

    @Test
    @DisplayName("Handle ServiceNow Unavailable HTTP 503 with retries")
    void fetchCertificates_serviceUnavailable() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/now/table/cmdb_ci_certificate"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable: Node Restarting")));

        assertThatThrownBy(() -> client.fetchCertificates("active=true", 10, 0, "TRACE-503"))
                .isInstanceOf(ServiceNowServerException.class)
                .hasMessageContaining("503");
    }

    @Test
    @DisplayName("Handle HTTP Timeout")
    void fetchCertificates_timeout() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/now/table/cmdb_ci_certificate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(2000))); // longer than 1000ms readTimeout

        assertThatThrownBy(() -> client.fetchCertificates("active=true", 10, 0, "TRACE-TIMEOUT"))
                .isInstanceOf(ServiceNowTimeoutException.class);
    }

    @Test
    @DisplayName("Handle Invalid / Malformed JSON response")
    void fetchCertificates_invalidResponse() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/now/table/cmdb_ci_certificate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("<html><body>Bad Gateway 502 HTML</body></html>")));

        assertThatThrownBy(() -> client.fetchCertificates("active=true", 10, 0, "TRACE-MALFORMED"))
                .isInstanceOf(ServiceNowParseException.class);
    }

    @Test
    @DisplayName("Handle Partial Data in ServiceNow responses with fallbacks")
    void fetchCertificates_partialData() {
        String jsonWithFallbacks = """
            {
              "result": [
                {
                  "sys_id": "partial-cert-01",
                  "name": "alias-cn.grassroots.internal",
                  "serial_number": "PARTIAL-1122",
                  "fingerprint": "AA:BB:CC:DD:EE",
                  "sans": "alias-cn.grassroots.internal",
                  "fqdn": "target-host-alias.grassroots.internal"
                }
              ]
            }
            """;

        wireMockServer.stubFor(get(urlPathEqualTo("/api/now/table/cmdb_ci_certificate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonWithFallbacks)));

        List<ServiceNowCertificateDto> result = client.fetchCertificates("active=true", 10, 0, "TRACE-PARTIAL");

        assertThat(result).hasSize(1);
        ServiceNowCertificateDto cert = result.get(0);
        assertThat(cert.getSysId()).isEqualTo("partial-cert-01");
        assertThat(cert.getCommonName()).isEqualTo("alias-cn.grassroots.internal");
        assertThat(cert.getThumbprint()).isEqualTo("AA:BB:CC:DD:EE");
        assertThat(cert.getTargetHost()).isEqualTo("target-host-alias.grassroots.internal");
        assertThat(cert.getSubjectAlternativeNames()).isEqualTo("alias-cn.grassroots.internal");
    }

    @Test
    @DisplayName("Update certificate status in ServiceNow via PATCH")
    void updateCertificateStatus_success() {
        wireMockServer.stubFor(patch(urlPathEqualTo("/api/now/table/cmdb_ci_certificate/sys-update-01"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"result\":{\"sys_id\":\"sys-update-01\",\"operational_status\":\"ACTIVE\"}}")));

        client.updateCertificateStatus("sys-update-01", "ACTIVE", "TRACE-UPDATE");

        WireMock.verify(WireMock.patchRequestedFor(urlPathEqualTo("/api/now/table/cmdb_ci_certificate/sys-update-01")));
    }
}
