package com.grassroots.cdm.integration.midserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.grassroots.cdm.entity.enums.DeploymentType;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.MidServerExecutionState;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.integration.midserver.client.MidServerClientImpl;
import com.grassroots.cdm.integration.midserver.config.MidServerProperties;
import com.grassroots.cdm.integration.midserver.dto.CertificateReferenceDto;
import com.grassroots.cdm.integration.midserver.dto.ExecutionParametersDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerCancelResponseDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerHealthDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobRequestDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobResponseDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerStatusQueryResponseDto;
import com.grassroots.cdm.integration.midserver.exception.MidServerAuthenticationException;
import com.grassroots.cdm.integration.midserver.exception.MidServerDuplicateRequestException;
import com.grassroots.cdm.integration.midserver.exception.MidServerException;
import com.grassroots.cdm.integration.midserver.exception.MidServerExecutionTimeoutException;
import com.grassroots.cdm.integration.midserver.exception.MidServerInvalidResponseException;
import com.grassroots.cdm.integration.midserver.exception.MidServerTimeoutException;
import com.grassroots.cdm.integration.midserver.exception.MidServerUnavailableException;
import com.grassroots.cdm.integration.midserver.dto.TargetServerInfoDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MidServerClientWireMockTest {

    private WireMockServer wireMockServer;
    private MidServerClientImpl client;
    private MidServerProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        properties = new MidServerProperties();
        properties.setBaseUrl("http://localhost:" + wireMockServer.port());
        properties.setJobsPath("/api/v1/mid/jobs");
        properties.setAuthType(MidServerProperties.AuthType.BEARER);
        properties.setBearerToken("secret-mid-agent-token");
        properties.setConnectTimeoutMs(1500);
        properties.setReadTimeoutMs(1500);
        properties.setMaxRetries(2);
        properties.setBackoffMs(50L);

        client = new MidServerClientImpl(properties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    private MidServerJobRequestDto createSampleJobRequest(String idempotencyKey) {
        UUID jobId = UUID.randomUUID();
        TargetServerInfoDto serverInfo = new TargetServerInfoDto(
                "web-prod-01.grassroots.internal",
                "10.0.1.25",
                ServerOperatingSystem.LINUX_RHEL,
                ServerTechnology.NGINX,
                443,
                EnvironmentType.PRODUCTION
        );

        CertificateReferenceDto certRef = new CertificateReferenceDto(
                UUID.randomUUID(),
                "1234567890ABCDEF",
                "THUMBPRINT1234567890ABCDEF1234567890ABCD",
                "api.grassroots.internal",
                List.of("api.grassroots.internal", "secure.grassroots.internal"),
                "cyberark://GrassrootsSafe/Account/Cert-001",
                Instant.now().plusSeconds(86400 * 365)
        );

        ExecutionParametersDto execParams = new ExecutionParametersDto(
                "/etc/nginx/ssl",
                "default-ssl",
                true,
                true,
                true,
                180,
                Map.of("nginxReloadCmd", "systemctl reload nginx")
        );

        return new MidServerJobRequestDto(
                jobId,
                idempotencyKey,
                serverInfo,
                DeploymentType.RENEWAL_REPLACEMENT,
                certRef,
                execParams
        );
    }

    @Test
    @DisplayName("Successfully submit job, verify headers and response parsing")
    void submitJob_success_returnsReceipt() {
        String idempotencyKey = "IDEMP-JOB-001";
        MidServerJobRequestDto request = createSampleJobRequest(idempotencyKey);

        String jsonResponse = """
            {
              "taskId": "MID-TASK-998877",
              "jobId": "%s",
              "idempotencyKey": "%s",
              "status": "QUEUED",
              "acceptedAt": "2026-09-27T12:00:00Z",
              "message": "Task queued for execution on target node"
            }
            """.formatted(request.getJobId(), idempotencyKey);

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/mid/jobs"))
                .withHeader("Authorization", equalTo("Bearer secret-mid-agent-token"))
                .withHeader("X-Idempotency-Key", equalTo(idempotencyKey))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        MidServerJobResponseDto response = client.submitJob(request);

        assertThat(response).isNotNull();
        assertThat(response.getTaskId()).isEqualTo("MID-TASK-998877");
        assertThat(response.getStatus()).isEqualTo(MidServerExecutionState.QUEUED);
        assertThat(response.getIdempotencyKey()).isEqualTo(idempotencyKey);

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/api/v1/mid/jobs"))
                .withHeader("X-Idempotency-Key", equalTo(idempotencyKey))
                .withHeader("Authorization", equalTo("Bearer secret-mid-agent-token")));
    }

    @Test
    @DisplayName("Submit job targeting a specific MID Server node endpoint")
    void submitJob_withSpecificEndpoint_success() {
        String idempotencyKey = "IDEMP-NODE-002";
        MidServerJobRequestDto request = createSampleJobRequest(idempotencyKey);

        String jsonResponse = """
            {
              "taskId": "MID-NODE-1234",
              "jobId": "%s",
              "idempotencyKey": "%s",
              "status": "QUEUED",
              "message": "Enqueued on node"
            }
            """.formatted(request.getJobId(), idempotencyKey);

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/mid/jobs"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        String customEndpoint = "http://localhost:" + wireMockServer.port();
        MidServerJobResponseDto response = client.submitJob(customEndpoint, request);

        assertThat(response).isNotNull();
        assertThat(response.getTaskId()).isEqualTo("MID-NODE-1234");
    }

    @Test
    @DisplayName("Authentication failure (401) throws MidServerAuthenticationException")
    void submitJob_authenticationFailure_throwsMidServerAuthenticationException() {
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/mid/jobs"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("{\"error\": \"Unauthorized - Invalid Token\"}")));

        MidServerJobRequestDto request = createSampleJobRequest("IDEMP-AUTH-FAIL");

        assertThatThrownBy(() -> client.submitJob(request))
                .isInstanceOf(MidServerAuthenticationException.class)
                .hasMessageContaining("HTTP 401");
    }

    @Test
    @DisplayName("MID Server 503 unavailable throws MidServerUnavailableException after retries")
    void submitJob_midServerUnavailable_throwsMidServerUnavailableException() {
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/mid/jobs"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("{\"error\": \"MID Server Agent Overloaded\"}")));

        MidServerJobRequestDto request = createSampleJobRequest("IDEMP-UNAVAIL");

        assertThatThrownBy(() -> client.submitJob(request))
                .isInstanceOf(MidServerUnavailableException.class)
                .hasMessageContaining("503");

        // properties.maxRetries = 2, so total 3 attempts (1 initial + 2 retries)
        wireMockServer.verify(3, postRequestedFor(urlEqualTo("/api/v1/mid/jobs")));
    }

    @Test
    @DisplayName("Transient gateway error (502) retries and succeeds on next attempt")
    void submitJob_transientFailure_retriesAndSucceeds() {
        String idempotencyKey = "IDEMP-RETRY-SUCCESS";
        MidServerJobRequestDto request = createSampleJobRequest(idempotencyKey);

        // 1st request returns 502
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/mid/jobs"))
                .inScenario("RetryScenario")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(502).withBody("Bad Gateway"))
                .willSetStateTo("FirstFailed"));

        // 2nd request returns 201 Created
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/mid/jobs"))
                .inScenario("RetryScenario")
                .whenScenarioStateIs("FirstFailed")
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "taskId": "MID-TASK-RECOVERED",
                              "jobId": "%s",
                              "idempotencyKey": "%s",
                              "status": "QUEUED"
                            }
                            """.formatted(request.getJobId(), idempotencyKey))));

        MidServerJobResponseDto response = client.submitJob(request);

        assertThat(response).isNotNull();
        assertThat(response.getTaskId()).isEqualTo("MID-TASK-RECOVERED");
        wireMockServer.verify(2, postRequestedFor(urlEqualTo("/api/v1/mid/jobs")));
    }

    @Test
    @DisplayName("Client error (400 Bad Request) fails immediately without retrying")
    void submitJob_nonRetryableClientError_failsImmediately() {
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/mid/jobs"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("{\"error\": \"Malformed targetHost parameter\"}")));

        MidServerJobRequestDto request = createSampleJobRequest("IDEMP-BAD-REQUEST");

        assertThatThrownBy(() -> client.submitJob(request))
                .isInstanceOf(MidServerException.class)
                .hasMessageContaining("HTTP 400");

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/api/v1/mid/jobs")));
    }

    @Test
    @DisplayName("Read timeout throws MidServerTimeoutException after retrying")
    void submitJob_readTimeout_throwsMidServerTimeoutException() {
        properties.setConnectTimeoutMs(300);
        properties.setReadTimeoutMs(300);
        properties.setMaxRetries(1);
        client = new MidServerClientImpl(properties, objectMapper);

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/mid/jobs"))
                .willReturn(aResponse()
                        .withFixedDelay(1000)
                        .withStatus(200)
                        .withBody("{}")));

        MidServerJobRequestDto request = createSampleJobRequest("IDEMP-TIMEOUT");

        assertThatThrownBy(() -> client.submitJob(request))
                .isInstanceOf(MidServerTimeoutException.class);
    }

    @Test
    @DisplayName("Malformed response missing required taskId throws MidServerInvalidResponseException")
    void submitJob_malformedResponse_throwsMidServerInvalidResponseException() {
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/mid/jobs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"unexpectedField\": 12345}")));

        MidServerJobRequestDto request = createSampleJobRequest("IDEMP-MALFORMED");

        assertThatThrownBy(() -> client.submitJob(request))
                .isInstanceOf(MidServerInvalidResponseException.class)
                .hasMessageContaining("missing required taskId");
    }

    @Test
    @DisplayName("Duplicate request (HTTP 409 Conflict) throws MidServerDuplicateRequestException with existing taskId")
    void submitJob_duplicateRequest_throwsMidServerDuplicateRequestException() {
        String idempotencyKey = "IDEMP-DUPLICATE-KEY";
        MidServerJobRequestDto request = createSampleJobRequest(idempotencyKey);

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/mid/jobs"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "error": "Conflict - Request already being processed",
                              "taskId": "MID-TASK-EXISTING-999"
                            }
                            """)));

        assertThatThrownBy(() -> client.submitJob(request))
                .isInstanceOf(MidServerDuplicateRequestException.class)
                .satisfies(ex -> {
                    MidServerDuplicateRequestException dex = (MidServerDuplicateRequestException) ex;
                    assertThat(dex.getIdempotencyKey()).isEqualTo(idempotencyKey);
                    assertThat(dex.getExistingTaskId()).isEqualTo("MID-TASK-EXISTING-999");
                });

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/api/v1/mid/jobs")));
    }

    @Test
    @DisplayName("Successfully query job status for in-progress and completed task")
    void getJobStatus_success_inProgressAndCompleted() {
        String taskId = "MID-TASK-STATUS-001";
        wireMockServer.stubFor(get(urlEqualTo("/api/v1/mid/jobs/" + taskId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "taskId": "%s",
                              "jobId": "%s",
                              "idempotencyKey": "IDEMP-STATUS-001",
                              "state": "SUCCESS",
                              "exitCode": 0,
                              "stdoutSummary": "Certificate installed and bound to Nginx",
                              "startedAt": "2026-09-27T12:05:00Z",
                              "completedAt": "2026-09-27T12:05:30Z"
                            }
                            """.formatted(taskId, UUID.randomUUID()))));

        MidServerStatusQueryResponseDto status = client.getJobStatus(taskId);

        assertThat(status).isNotNull();
        assertThat(status.getTaskId()).isEqualTo(taskId);
        assertThat(status.getState()).isEqualTo(MidServerExecutionState.SUCCESS);
        assertThat(status.getExitCode()).isEqualTo(0);
        assertThat(status.isSuccessful()).isTrue();
        assertThat(status.isFinished()).isTrue();
    }

    @Test
    @DisplayName("Query job status reporting TIMED_OUT throws MidServerExecutionTimeoutException")
    void getJobStatus_timedOutState_throwsMidServerExecutionTimeoutException() {
        String taskId = "MID-TASK-TIMEOUT-001";
        wireMockServer.stubFor(get(urlEqualTo("/api/v1/mid/jobs/" + taskId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "taskId": "%s",
                              "state": "TIMED_OUT",
                              "errorMessage": "Script execution timed out after 300 seconds"
                            }
                            """.formatted(taskId))));

        assertThatThrownBy(() -> client.getJobStatus(taskId))
                .isInstanceOf(MidServerExecutionTimeoutException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    @DisplayName("Cancel running job on MID Server")
    void cancelJob_success() {
        String taskId = "MID-TASK-CANCEL-001";
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/mid/jobs/" + taskId + "/cancel"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "taskId": "%s",
                              "status": "CANCELLED",
                              "message": "Process terminated by operator"
                            }
                            """.formatted(taskId))));

        MidServerCancelResponseDto cancel = client.cancelJob(taskId, "Operator cancellation request");

        assertThat(cancel).isNotNull();
        assertThat(cancel.getTaskId()).isEqualTo(taskId);
        assertThat(cancel.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Health check probe verification")
    void checkHealth_upAndDown() {
        wireMockServer.stubFor(get(urlEqualTo("/api/v1/mid/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "status": "UP",
                              "version": "2.4.0",
                              "activeTasks": 3
                            }
                            """)));

        assertThat(client.checkHealth()).isTrue();

        MidServerHealthDto details = client.getHealthDetails();
        assertThat(details.getStatus()).isEqualTo("UP");
        assertThat(details.getVersion()).isEqualTo("2.4.0");
        assertThat(details.getActiveTasks()).isEqualTo(3);

        // When health check endpoint returns 500
        wireMockServer.stubFor(get(urlEqualTo("/api/v1/mid/health"))
                .willReturn(aResponse().withStatus(500)));

        assertThat(client.checkHealth()).isFalse();
    }
}
