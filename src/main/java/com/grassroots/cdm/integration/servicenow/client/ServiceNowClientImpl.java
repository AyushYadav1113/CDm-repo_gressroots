package com.grassroots.cdm.integration.servicenow.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grassroots.cdm.integration.servicenow.ServiceNowClient;
import com.grassroots.cdm.integration.servicenow.config.ServiceNowProperties;
import com.grassroots.cdm.integration.servicenow.dto.ServiceNowCertificateDto;
import com.grassroots.cdm.integration.servicenow.dto.ServiceNowErrorResponse;
import com.grassroots.cdm.integration.servicenow.dto.ServiceNowTableResponse;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowAuthenticationException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowClientException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowParseException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowRateLimitException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowServerException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link ServiceNowClient} using Spring 6's {@link RestClient}.
 * Encapsulates HTTP transport, timeout management, retries with exponential backoff,
 * authentication, and error translation.
 */
@Component
@ConditionalOnProperty(name = "cdm.servicenow.mock-enabled", havingValue = "false", matchIfMissing = true)
public class ServiceNowClientImpl implements ServiceNowClient {

    private static final Logger log = LoggerFactory.getLogger(ServiceNowClientImpl.class);
    private static final String TABLE_API_PATH = "/api/now/table/";

    private final ServiceNowProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public ServiceNowClientImpl(ServiceNowProperties properties, ObjectMapper objectMapper) {
        this(properties, buildDefaultRestClient(properties), objectMapper);
    }

    public ServiceNowClientImpl(ServiceNowProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    private static RestClient buildDefaultRestClient(ServiceNowProperties props) {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();
        org.springframework.http.client.JdkClientHttpRequestFactory factory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (props.getBaseUrl() != null && !props.getBaseUrl().isBlank()) {
            builder.baseUrl(props.getBaseUrl());
        }

        if (props.getBearerToken() != null && !props.getBearerToken().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getBearerToken());
        } else if (props.getUsername() != null && !props.getUsername().isBlank()
                && props.getPassword() != null && !props.getPassword().isBlank()) {
            String auth = props.getUsername() + ":" + props.getPassword();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth);
        }

        return builder.build();
    }

    @Override
    public List<ServiceNowCertificateDto> fetchCertificates(String queryFilter, int limit, int offset, String correlationId) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        String table = properties.getTable() != null ? properties.getTable() : "cmdb_ci_certificate";
        String path = TABLE_API_PATH + table;

        log.info("Fetching certificates from ServiceNow: path={}, limit={}, offset={}, correlationId={}",
                path, limit, offset, resolvedCorrelationId);

        return executeWithRetry(() -> {
            try {
                String responseBody = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path(path)
                                .queryParam("sysparm_query", queryFilter != null ? queryFilter : properties.getQueryFilter())
                                .queryParam("sysparm_limit", limit > 0 ? limit : properties.getPageSize())
                                .queryParam("sysparm_offset", offset)
                                .queryParam("sysparm_display_value", "false")
                                .build())
                        .header("X-Correlation-ID", resolvedCorrelationId)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, resp) -> {
                            handleHttpError(resp.getStatusCode().value(), resp.getBody().readAllBytes(), resp.getHeaders());
                        })
                        .body(String.class);

                return parseCertificateListResponse(responseBody);
            } catch (RestClientResponseException ex) {
                handleHttpError(ex.getStatusCode().value(), ex.getResponseBodyAsByteArray(), ex.getResponseHeaders());
                return Collections.emptyList();
            } catch (ResourceAccessException ex) {
                throw translateResourceAccessException(ex);
            } catch (ServiceNowException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ServiceNowParseException("Failed reading response from ServiceNow: " + ex.getMessage(), ex);
            }
        }, resolvedCorrelationId);
    }

    @Override
    public List<ServiceNowCertificateDto> fetchAllCertificates(String correlationId) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        List<ServiceNowCertificateDto> allCertificates = new ArrayList<>();
        int pageSize = properties.getPageSize() > 0 ? properties.getPageSize() : 100;
        int offset = 0;
        boolean hasMore = true;

        log.info("Starting complete certificate synchronization from ServiceNow (correlationId={})", resolvedCorrelationId);

        while (hasMore) {
            List<ServiceNowCertificateDto> page = fetchCertificates(properties.getQueryFilter(), pageSize, offset, resolvedCorrelationId);
            if (page == null || page.isEmpty()) {
                hasMore = false;
            } else {
                allCertificates.addAll(page);
                if (page.size() < pageSize) {
                    hasMore = false;
                } else {
                    offset += page.size();
                }
            }
        }

        log.info("Completed ServiceNow discovery fetch: totalRecords={}, correlationId={}",
                allCertificates.size(), resolvedCorrelationId);
        return allCertificates;
    }

    @Override
    public Optional<ServiceNowCertificateDto> fetchCertificateBySysId(String sysId, String correlationId) {
        if (sysId == null || sysId.isBlank()) {
            return Optional.empty();
        }
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        String table = properties.getTable() != null ? properties.getTable() : "cmdb_ci_certificate";
        String path = TABLE_API_PATH + table + "/" + sysId;

        return executeWithRetry(() -> {
            try {
                String responseBody = restClient.get()
                        .uri(path)
                        .header("X-Correlation-ID", resolvedCorrelationId)
                        .retrieve()
                        .onStatus(status -> status.value() == 404, (req, resp) -> {
                            // 404 is handled as empty Optional
                        })
                        .onStatus(HttpStatusCode::isError, (req, resp) -> {
                            handleHttpError(resp.getStatusCode().value(), resp.getBody().readAllBytes(), resp.getHeaders());
                        })
                        .body(String.class);

                if (responseBody == null || responseBody.isBlank()) {
                    return Optional.empty();
                }

                return parseSingleCertificateResponse(responseBody);
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() == 404) {
                    return Optional.empty();
                }
                handleHttpError(ex.getStatusCode().value(), ex.getResponseBodyAsByteArray(), ex.getResponseHeaders());
                return Optional.empty();
            } catch (ResourceAccessException ex) {
                throw translateResourceAccessException(ex);
            } catch (ServiceNowException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ServiceNowParseException("Failed reading response from ServiceNow: " + ex.getMessage(), ex);
            }
        }, resolvedCorrelationId);
    }

    @Override
    public void updateCertificateStatus(String sysId, String status, String correlationId) {
        if (sysId == null || sysId.isBlank()) {
            return;
        }
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        String table = properties.getTable() != null ? properties.getTable() : "cmdb_ci_certificate";
        String path = TABLE_API_PATH + table + "/" + sysId;

        Map<String, String> payload = Map.of(
                "operational_status", status,
                "state", status
        );

        executeWithRetry(() -> {
            try {
                restClient.patch()
                        .uri(path)
                        .header("X-Correlation-ID", resolvedCorrelationId)
                        .body(payload)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, resp) -> {
                            handleHttpError(resp.getStatusCode().value(), resp.getBody().readAllBytes(), resp.getHeaders());
                        })
                        .toBodilessEntity();
                log.info("Updated ServiceNow certificate status: sysId={}, status={}, correlationId={}",
                        sysId, status, resolvedCorrelationId);
                return null;
            } catch (RestClientResponseException ex) {
                handleHttpError(ex.getStatusCode().value(), ex.getResponseBodyAsByteArray(), ex.getResponseHeaders());
                return null;
            } catch (ResourceAccessException ex) {
                throw translateResourceAccessException(ex);
            }
        }, resolvedCorrelationId);
    }

    private List<ServiceNowCertificateDto> parseCertificateListResponse(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            // First try Table API envelope: { "result": [ ... ] }
            ServiceNowTableResponse<List<ServiceNowCertificateDto>> envelope = objectMapper.readValue(
                    json, new TypeReference<>() {});
            if (envelope != null && envelope.getResult() != null) {
                return envelope.getResult();
            }

            // Fallback: direct JSON array [ ... ]
            return objectMapper.readValue(json, new TypeReference<List<ServiceNowCertificateDto>>() {});
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse ServiceNow certificate list response: {}", json, ex);
            throw new ServiceNowParseException("Malformed JSON response from ServiceNow: " + ex.getMessage(), ex);
        }
    }

    private Optional<ServiceNowCertificateDto> parseSingleCertificateResponse(String json) {
        try {
            ServiceNowTableResponse<ServiceNowCertificateDto> envelope = objectMapper.readValue(
                    json, new TypeReference<>() {});
            if (envelope != null && envelope.getResult() != null) {
                return Optional.of(envelope.getResult());
            }

            ServiceNowCertificateDto direct = objectMapper.readValue(json, ServiceNowCertificateDto.class);
            return Optional.ofNullable(direct);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse ServiceNow single certificate response: {}", json, ex);
            throw new ServiceNowParseException("Malformed JSON response from ServiceNow: " + ex.getMessage(), ex);
        }
    }

    private void handleHttpError(int statusCode, byte[] responseBodyBytes, HttpHeaders headers) {
        String responseBody = responseBodyBytes != null ? new String(responseBodyBytes, StandardCharsets.UTF_8) : "";
        String errorMessage = extractErrorMessage(responseBody);

        log.warn("ServiceNow API returned HTTP error: status={}, error={}", statusCode, errorMessage);

        if (statusCode == 401 || statusCode == 403) {
            throw new ServiceNowAuthenticationException("ServiceNow authentication failed: " + errorMessage, statusCode);
        } else if (statusCode == 429) {
            Long retryAfter = parseRetryAfter(headers);
            throw new ServiceNowRateLimitException("ServiceNow rate limit exceeded: " + errorMessage, retryAfter);
        } else if (statusCode >= 400 && statusCode < 500) {
            throw new ServiceNowClientException("ServiceNow client error: " + errorMessage, statusCode);
        } else if (statusCode >= 500) {
            throw new ServiceNowServerException("ServiceNow server error: " + errorMessage, statusCode);
        } else {
            throw new ServiceNowException("ServiceNow unexpected error: " + errorMessage, statusCode);
        }
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "No response body provided";
        }
        try {
            ServiceNowErrorResponse errorResponse = objectMapper.readValue(body, ServiceNowErrorResponse.class);
            if (errorResponse != null) {
                return errorResponse.getFormattedMessage();
            }
        } catch (Exception ignored) {
            // Not ServiceNow error schema, return raw body truncated
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    private Long parseRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String retryAfterHeader = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfterHeader != null) {
            try {
                return Long.parseLong(retryAfterHeader);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private ServiceNowException translateResourceAccessException(ResourceAccessException ex) {
        if (ex.getCause() instanceof SocketTimeoutException || ex.getMessage().contains("timed out")) {
            return new ServiceNowTimeoutException("ServiceNow connection timed out: " + ex.getMessage(), ex);
        }
        return new ServiceNowServerException("ServiceNow unreachable: " + ex.getMessage(), 503, ex);
    }

    private <T> T executeWithRetry(RetryableOperation<T> operation, String correlationId) {
        int attempts = 0;
        int maxAttempts = Math.max(1, properties.getMaxRetries());
        long backoff = Math.max(100, properties.getBackoffMs());

        while (true) {
            attempts++;
            try {
                return operation.execute();
            } catch (ServiceNowAuthenticationException | ServiceNowClientException | ServiceNowParseException ex) {
                // Non-retryable errors
                throw ex;
            } catch (ServiceNowRateLimitException ex) {
                if (attempts >= maxAttempts) {
                    throw ex;
                }
                long waitTime = ex.getRetryAfterSeconds() != null ? ex.getRetryAfterSeconds() * 1000 : backoff * attempts;
                log.warn("Rate limited by ServiceNow (attempt {}/{}). Waiting {}ms before retry...", attempts, maxAttempts, waitTime);
                sleep(waitTime);
            } catch (ServiceNowTimeoutException | ServiceNowServerException ex) {
                if (attempts >= maxAttempts) {
                    throw ex;
                }
                long waitTime = backoff * (1L << (attempts - 1)); // exponential backoff
                log.warn("Retryable error from ServiceNow (attempt {}/{}): {}. Retrying in {}ms...",
                        attempts, maxAttempts, ex.getMessage(), waitTime);
                sleep(waitTime);
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceNowException("Operation interrupted during retry backoff", ex);
        }
    }

    private String resolveCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        String mdcCorrelation = MDC.get("correlationId");
        if (mdcCorrelation != null && !mdcCorrelation.isBlank()) {
            return mdcCorrelation;
        }
        return UUID.randomUUID().toString();
    }

    @FunctionalInterface
    private interface RetryableOperation<T> {
        T execute();
    }
}
