package com.grassroots.cdm.credential;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.grassroots.cdm.credential.cyberark.CyberArkProperties;
import com.grassroots.cdm.credential.cyberark.DefaultCyberArkClient;
import com.grassroots.cdm.credential.dto.CyberArkAccountResponse;
import com.grassroots.cdm.credential.exception.CredentialExpiredException;
import com.grassroots.cdm.credential.exception.CredentialNotFoundException;
import com.grassroots.cdm.credential.exception.CredentialRotationInProgressException;
import com.grassroots.cdm.credential.exception.CyberArkUnavailableException;
import com.grassroots.cdm.credential.exception.CyberArkUnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCyberArkClientWireMockTest {

    private WireMockServer wireMockServer;
    private DefaultCyberArkClient client;
    private CyberArkProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        properties = new CyberArkProperties();
        properties.setBaseUrl(wireMockServer.baseUrl() + "/AIMWebService");
        properties.setAppId("CDM_Application");
        properties.setDefaultSafe("GrassrootsSafe");
        properties.setConnectTimeoutMs(2000);
        properties.setReadTimeoutMs(3000);
        properties.setMaxRetries(2);
        properties.setRetryBackoffMs(50);

        client = new DefaultCyberArkClient(properties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("CyberArk CCP: Successfully fetches account credentials and parses DTO")
    void testGetAccountSuccess() {
        String jsonResponse = """
                {
                  "Content": "VaultSecret123!",
                  "UserName": "svc_cdm_deployer",
                  "Address": "web01.internal",
                  "Safe": "GrassrootsSafe",
                  "Folder": "Root",
                  "Name": "svc_cdm_deployer",
                  "PolicyID": "WinDesktopLocal",
                  "DeviceType": "Operating System",
                  "PasswordChangeInProcess": false,
                  "IsExpired": false
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/AIMWebService/api/Accounts"))
                .withQueryParam("AppID", equalTo("CDM_Application"))
                .withQueryParam("Safe", equalTo("GrassrootsSafe"))
                .withQueryParam("Object", equalTo("svc_cdm_deployer"))
                .withQueryParam("Address", equalTo("web01.internal"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        CyberArkAccountResponse response = client.getAccount("GrassrootsSafe", "svc_cdm_deployer", "web01.internal");

        assertThat(response).isNotNull();
        assertThat(response.getUserName()).isEqualTo("svc_cdm_deployer");
        assertThat(response.getContent()).containsExactly("VaultSecret123!".toCharArray());
        assertThat(response.getAddress()).isEqualTo("web01.internal");
        assertThat(response.isPasswordChangeInProcess()).isFalse();
        assertThat(response.isExpired()).isFalse();
    }

    @Test
    @DisplayName("Credential Rotation: Throws CredentialRotationInProgressException when PasswordChangeInProcess is true")
    void testPasswordRotationInProgressPayload() {
        String jsonResponse = """
                {
                  "Content": "OldPassword!",
                  "UserName": "svc_cdm_deployer",
                  "Safe": "GrassrootsSafe",
                  "Name": "svc_cdm_deployer",
                  "PasswordChangeInProcess": true
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/AIMWebService/api/Accounts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        assertThatThrownBy(() -> client.getAccount("GrassrootsSafe", "svc_cdm_deployer", null))
                .isInstanceOf(CredentialRotationInProgressException.class)
                .hasMessageContaining("rotation is currently in progress");
    }

    @Test
    @DisplayName("Credential Rotation: HTTP 409 Conflict triggers CredentialRotationInProgressException")
    void testPasswordRotationHttp409() {
        wireMockServer.stubFor(get(urlPathEqualTo("/AIMWebService/api/Accounts"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ErrorCode\": \"APPAP314E\", \"ErrorMsg\": \"Password change in progress\"}")));

        assertThatThrownBy(() -> client.getAccount("GrassrootsSafe", "svc_cdm_deployer", null))
                .isInstanceOf(CredentialRotationInProgressException.class);
    }

    @Test
    @DisplayName("Unauthorized Access: HTTP 401 / 403 triggers CyberArkUnauthorizedException")
    void testUnauthorizedHttp403() {
        wireMockServer.stubFor(get(urlPathEqualTo("/AIMWebService/api/Accounts"))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ErrorCode\": \"APPAP133E\", \"ErrorMsg\": \"AppID not authorized for safe\"}")));

        assertThatThrownBy(() -> client.getAccount("GrassrootsSafe", "svc_cdm_deployer", null))
                .isInstanceOf(CyberArkUnauthorizedException.class)
                .hasMessageContaining("CDM_Application")
                .hasMessageContaining("GrassrootsSafe");
    }

    @Test
    @DisplayName("Missing Credential: HTTP 404 triggers CredentialNotFoundException")
    void testNotFoundHttp404() {
        wireMockServer.stubFor(get(urlPathEqualTo("/AIMWebService/api/Accounts"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ErrorCode\": \"APPAP002E\", \"ErrorMsg\": \"Object does not exist\"}")));

        assertThatThrownBy(() -> client.getAccount("GrassrootsSafe", "unknown_account", null))
                .isInstanceOf(CredentialNotFoundException.class)
                .hasMessageContaining("unknown_account");
    }

    @Test
    @DisplayName("Expired Credential: IsExpired true triggers CredentialExpiredException")
    void testExpiredCredential() {
        String jsonResponse = """
                {
                  "Content": "ExpiredPassword!",
                  "UserName": "svc_cdm_deployer",
                  "Safe": "GrassrootsSafe",
                  "Name": "svc_cdm_deployer",
                  "PasswordChangeInProcess": false,
                  "IsExpired": true
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/AIMWebService/api/Accounts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        assertThatThrownBy(() -> client.getAccount("GrassrootsSafe", "svc_cdm_deployer", null))
                .isInstanceOf(CredentialExpiredException.class)
                .hasMessageContaining("expired in CyberArk");
    }

    @Test
    @DisplayName("Unavailable CyberArk: HTTP 503 triggers CyberArkUnavailableException")
    void testUnavailableCyberArk() {
        wireMockServer.stubFor(get(urlPathEqualTo("/AIMWebService/api/Accounts"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        assertThatThrownBy(() -> client.getAccount("GrassrootsSafe", "svc_cdm_deployer", null))
                .isInstanceOf(CyberArkUnavailableException.class)
                .hasMessageContaining("503");
    }

    @Test
    @DisplayName("Transient Failure: 500 error on attempt 1 succeeds after retry")
    void testTransientFailureRetrySuccess() {
        String jsonResponse = """
                {
                  "Content": "RetrySuccessPassword!",
                  "UserName": "svc_cdm_deployer",
                  "Safe": "GrassrootsSafe",
                  "Name": "svc_cdm_deployer"
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/AIMWebService/api/Accounts"))
                .inScenario("RetryScenario")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(500).withBody("Internal Error"))
                .willSetStateTo("RetrySuccessState"));

        wireMockServer.stubFor(get(urlPathEqualTo("/AIMWebService/api/Accounts"))
                .inScenario("RetryScenario")
                .whenScenarioStateIs("RetrySuccessState")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        CyberArkAccountResponse response = client.getAccount("GrassrootsSafe", "svc_cdm_deployer", null);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).containsExactly("RetrySuccessPassword!".toCharArray());
    }

    @Test
    @DisplayName("Ping: Reports online status accurately")
    void testPing() {
        wireMockServer.stubFor(get(urlPathEqualTo("/AIMWebService"))
                .willReturn(aResponse().withStatus(200)));

        assertThat(client.ping()).isTrue();

        wireMockServer.stop();
        assertThat(client.ping()).isFalse();
    }
}
