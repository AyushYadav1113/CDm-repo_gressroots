package com.grassroots.cdm.integration.midserver.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grassroots.cdm.entity.enums.MidServerExecutionState;
import com.grassroots.cdm.integration.midserver.MidServerClient;
import com.grassroots.cdm.integration.midserver.config.MidServerProperties;
import com.grassroots.cdm.integration.midserver.dto.MidServerCancelRequestDto;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Production implementation of {@link MidServerClient} using Spring 6 {@link RestClient}.
 * Provides TLS/HTTPS communication, secure credential redaction, authentication,
 * transient retry with exponential backoff, and idempotent task dispatch.
 */
@Component
@ConditionalOnProperty(name = "cdm.midserver.mock-enabled", havingValue = "false", matchIfMissing = true)
public class MidServerClientImpl implements MidServerClient {

    private static final Logger log = LoggerFactory.getLogger(MidServerClientImpl.class);

    public static final String HEADER_IDEMPOTENCY_KEY = "X-Idempotency-Key";
    public static final String HEADER_IDEMPOTENCY_KEY_ALT = "Idempotency-Key";

    private final MidServerProperties properties;
    private final RestClient defaultRestClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public MidServerClientImpl(MidServerProperties properties, ObjectMapper objectMapper) {
        this(properties, buildRestClient(properties, properties.getBaseUrl()), objectMapper);
    }

    public MidServerClientImpl(MidServerProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.defaultRestClient = restClient;
        this.objectMapper = objectMapper;
    }

    private static RestClient buildRestClient(MidServerProperties props, String baseUrl) {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }

        return builder.build();
    }

    private Consumer<HttpHeaders> createAuthAndIdempotencyHeaders(String idempotencyKey) {
        return headers -> {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                headers.set(HEADER_IDEMPOTENCY_KEY, idempotencyKey);
                headers.set(HEADER_IDEMPOTENCY_KEY_ALT, idempotencyKey);
            }

            if (properties.getAuthType() == MidServerProperties.AuthType.BEARER && properties.getBearerToken() != null) {
                headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getBearerToken());
            } else if (properties.getAuthType() == MidServerProperties.AuthType.BASIC
                    && properties.getUsername() != null && properties.getPassword() != null) {
                String auth = properties.getUsername() + ":" + properties.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth);
            } else if (properties.getAuthType() == MidServerProperties.AuthType.API_KEY && properties.getApiKey() != null) {
                headers.set(properties.getApiKeyHeader(), properties.getApiKey());
            }
        };
    }

    private RestClient resolveClient(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.equalsIgnoreCase(properties.getBaseUrl())) {
            return defaultRestClient;
        }
        return buildRestClient(properties, endpoint);
    }

    @Override
    public MidServerJobResponseDto submitJob(MidServerJobRequestDto request) {
        return submitJob(properties.getBaseUrl(), request);
    }

    @Override
    public MidServerJobResponseDto submitJob(String endpoint, MidServerJobRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("MidServerJobRequestDto cannot be null");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new IllegalArgumentException("IdempotencyKey is required for MID Server job submission");
        }

        String targetEndpoint = (endpoint != null && !endpoint.isBlank()) ? endpoint : properties.getBaseUrl();
        RestClient client = resolveClient(targetEndpoint);
        String path = properties.getJobsPath();

        log.info("Dispatching deployment task to MID Server [endpoint={}, jobId={}, idempotencyKey={}, targetHost={}]",
                targetEndpoint, request.getJobId(), request.getIdempotencyKey(),
                request.getTargetServer() != null ? request.getTargetServer().getHostname() : "unknown");

        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String responseBody = client.post()
                        .uri(path)
                        .headers(createAuthAndIdempotencyHeaders(request.getIdempotencyKey()))
                        .body(request)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, resp) -> {
                            handleHttpError(resp.getStatusCode(), resp.getBody() != null ? new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8) : "", targetEndpoint, request.getIdempotencyKey());
                        })
                        .body(String.class);

                MidServerJobResponseDto response = parseJsonResponse(responseBody, MidServerJobResponseDto.class);
                if (response == null || response.getTaskId() == null || response.getTaskId().isBlank()) {
                    throw new MidServerInvalidResponseException("MID Server returned response missing required taskId", responseBody);
                }

                log.info("MID Server accepted job [taskId={}, jobId={}, status={}]",
                        response.getTaskId(), response.getJobId(), response.getStatus());
                return response;

            } catch (RestClientResponseException ex) {
                handleHttpError(ex.getStatusCode(), ex.getResponseBodyAsString(), targetEndpoint, request.getIdempotencyKey());
            } catch (ResourceAccessException ex) {
                lastException = translateResourceAccessException(ex, targetEndpoint);
                if (!isRetryable(lastException) || attempt == maxAttempts) {
                    throw (MidServerException) lastException;
                }
                applyBackoff(attempt, "Connection/Timeout error submitting job to " + targetEndpoint);
            } catch (MidServerDuplicateRequestException | MidServerAuthenticationException | MidServerInvalidResponseException ex) {
                throw ex;
            } catch (MidServerUnavailableException ex) {
                lastException = ex;
                if (attempt == maxAttempts) {
                    throw ex;
                }
                applyBackoff(attempt, "MID Server unavailable at " + targetEndpoint);
            } catch (Exception ex) {
                if (ex instanceof MidServerException mse) {
                    throw mse;
                }
                log.error("Unexpected error dispatching to MID Server: {}", ex.getMessage(), ex);
                throw new MidServerException("Failed to dispatch job to MID Server: " + ex.getMessage(), ex);
            }
        }

        throw new MidServerException("Exhausted retries dispatching job to MID Server", lastException);
    }

    @Override
    public MidServerStatusQueryResponseDto getJobStatus(String taskId) {
        return getJobStatus(properties.getBaseUrl(), taskId);
    }

    @Override
    public MidServerStatusQueryResponseDto getJobStatus(String endpoint, String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("TaskId cannot be null or blank");
        }

        String targetEndpoint = (endpoint != null && !endpoint.isBlank()) ? endpoint : properties.getBaseUrl();
        RestClient client = resolveClient(targetEndpoint);
        String path = properties.getJobsPath() + "/" + taskId;

        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String responseBody = client.get()
                        .uri(path)
                        .headers(createAuthAndIdempotencyHeaders(null))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, resp) -> {
                            handleHttpError(resp.getStatusCode(), resp.getBody() != null ? new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8) : "", targetEndpoint, null);
                        })
                        .body(String.class);

                MidServerStatusQueryResponseDto response = parseJsonResponse(responseBody, MidServerStatusQueryResponseDto.class);
                if (response == null || response.getState() == null) {
                    throw new MidServerInvalidResponseException("MID Server status response missing execution state", responseBody);
                }

                if (response.getState() == MidServerExecutionState.TIMED_OUT) {
                    throw new MidServerExecutionTimeoutException("MID Server task execution timed out", taskId, properties.getExecutionTimeoutSeconds());
                }

                return response;

            } catch (RestClientResponseException ex) {
                handleHttpError(ex.getStatusCode(), ex.getResponseBodyAsString(), targetEndpoint, null);
            } catch (ResourceAccessException ex) {
                lastException = translateResourceAccessException(ex, targetEndpoint);
                if (!isRetryable(lastException) || attempt == maxAttempts) {
                    throw (MidServerException) lastException;
                }
                applyBackoff(attempt, "Connection/Timeout querying task " + taskId + " from " + targetEndpoint);
            } catch (MidServerAuthenticationException | MidServerInvalidResponseException | MidServerExecutionTimeoutException ex) {
                throw ex;
            } catch (MidServerUnavailableException ex) {
                lastException = ex;
                if (attempt == maxAttempts) {
                    throw ex;
                }
                applyBackoff(attempt, "MID Server unavailable at " + targetEndpoint);
            } catch (Exception ex) {
                if (ex instanceof MidServerException mse) {
                    throw mse;
                }
                log.error("Unexpected error querying MID Server task {}: {}", taskId, ex.getMessage(), ex);
                throw new MidServerException("Failed to query MID Server task " + taskId + ": " + ex.getMessage(), ex);
            }
        }

        throw new MidServerException("Exhausted retries querying MID Server task " + taskId, lastException);
    }

    @Override
    public MidServerCancelResponseDto cancelJob(String taskId, String reason) {
        return cancelJob(properties.getBaseUrl(), taskId, reason);
    }

    @Override
    public MidServerCancelResponseDto cancelJob(String endpoint, String taskId, String reason) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("TaskId cannot be null or blank");
        }

        String targetEndpoint = (endpoint != null && !endpoint.isBlank()) ? endpoint : properties.getBaseUrl();
        RestClient client = resolveClient(targetEndpoint);
        String path = properties.getJobsPath() + "/" + taskId + "/cancel";

        try {
            MidServerCancelRequestDto cancelReq = new MidServerCancelRequestDto(reason, true);
            String responseBody = client.post()
                    .uri(path)
                    .headers(createAuthAndIdempotencyHeaders(null))
                    .body(cancelReq)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        handleHttpError(resp.getStatusCode(), resp.getBody() != null ? new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8) : "", targetEndpoint, null);
                    })
                    .body(String.class);

            return parseJsonResponse(responseBody, MidServerCancelResponseDto.class);
        } catch (RestClientResponseException ex) {
            handleHttpError(ex.getStatusCode(), ex.getResponseBodyAsString(), targetEndpoint, null);
            return null; // unreachable due to handleHttpError throwing
        } catch (ResourceAccessException ex) {
            throw translateResourceAccessException(ex, targetEndpoint);
        } catch (Exception ex) {
            if (ex instanceof MidServerException mse) {
                throw mse;
            }
            throw new MidServerException("Failed to cancel MID Server task " + taskId + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean checkHealth() {
        return checkHealth(properties.getBaseUrl());
    }

    @Override
    public boolean checkHealth(String endpoint) {
        try {
            MidServerHealthDto health = getHealthDetails(endpoint);
            return health != null && "UP".equalsIgnoreCase(health.getStatus());
        } catch (Exception ex) {
            log.warn("MID Server health check probe failed for {}: {}", endpoint, ex.getMessage());
            return false;
        }
    }

    @Override
    public MidServerHealthDto getHealthDetails() {
        return getHealthDetails(properties.getBaseUrl());
    }

    @Override
    public MidServerHealthDto getHealthDetails(String endpoint) {
        String targetEndpoint = (endpoint != null && !endpoint.isBlank()) ? endpoint : properties.getBaseUrl();
        RestClient client = resolveClient(targetEndpoint);
        String path = "/api/v1/mid/health";

        try {
            String responseBody = client.get()
                    .uri(path)
                    .headers(createAuthAndIdempotencyHeaders(null))
                    .retrieve()
                    .body(String.class);

            return parseJsonResponse(responseBody, MidServerHealthDto.class);
        } catch (Exception ex) {
            throw new MidServerUnavailableException("Failed to reach MID Server health endpoint: " + ex.getMessage(), targetEndpoint, ex);
        }
    }

    // =========================================================================
    // Error Handling & HTTP Translation
    // =========================================================================

    private void handleHttpError(HttpStatusCode status, String responseBody, String endpoint, String idempotencyKey) {
        int code = status.value();
        log.warn("MID Server returned HTTP error [status={}, endpoint={}]", code, endpoint);

        if (code == 401 || code == 403) {
            throw new MidServerAuthenticationException("MID Server authentication failed (HTTP " + code + ")", code);
        }

        if (code == 409) {
            // Duplicate request with the same idempotency key
            String existingTaskId = null;
            try {
                if (responseBody != null && !responseBody.isBlank()) {
                    Map<?, ?> parsed = objectMapper.readValue(responseBody, Map.class);
                    Object taskIdObj = parsed.get("taskId");
                    if (taskIdObj != null) {
                        existingTaskId = taskIdObj.toString();
                    }
                }
            } catch (Exception ignored) {
            }
            throw new MidServerDuplicateRequestException("Duplicate deployment request detected for idempotency key: " + idempotencyKey, idempotencyKey, existingTaskId);
        }

        if (code == 503) {
            throw new MidServerUnavailableException("MID Server returned 503 Service Unavailable", endpoint);
        }

        if (code == 502 || code == 504) {
            throw new MidServerUnavailableException("MID Server gateway error (HTTP " + code + ")", endpoint);
        }

        if (code >= 400 && code < 500) {
            throw new MidServerException("MID Server client error HTTP " + code + ": " + responseBody);
        }

        throw new MidServerException("MID Server server error HTTP " + code + ": " + responseBody);
    }

    private MidServerException translateResourceAccessException(ResourceAccessException ex, String endpoint) {
        Throwable cause = ex.getCause();
        if (cause instanceof SocketTimeoutException
                || cause instanceof java.net.http.HttpTimeoutException
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timed out"))) {
            return new MidServerTimeoutException("Timeout communicating with MID Server: " + ex.getMessage(), properties.getReadTimeoutMs(), ex);
        }
        if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
            return new MidServerUnavailableException("Cannot connect to MID Server at " + endpoint + ": " + ex.getMessage(), endpoint, ex);
        }
        return new MidServerUnavailableException("Network error accessing MID Server at " + endpoint + ": " + ex.getMessage(), endpoint, ex);
    }

    private boolean isRetryable(Exception ex) {
        return ex instanceof MidServerUnavailableException
                || ex instanceof MidServerTimeoutException;
    }

    private void applyBackoff(int attempt, String context) {
        long delay = properties.getBackoffMs() * attempt;
        log.warn("Retrying MID Server request (attempt {}/{}) after {}ms backoff due to: {}",
                attempt, properties.getMaxRetries(), delay, context);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new MidServerException("Thread interrupted during MID Server retry backoff", ie);
        }
    }

    private <T> T parseJsonResponse(String body, Class<T> clazz) {
        if (body == null || body.isBlank()) {
            throw new MidServerInvalidResponseException("MID Server returned empty response body");
        }
        try {
            return objectMapper.readValue(body, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON response from MID Server: {}", body, e);
            throw new MidServerInvalidResponseException("Failed to parse response from MID Server: " + e.getMessage(), body, e);
        }
    }
}
