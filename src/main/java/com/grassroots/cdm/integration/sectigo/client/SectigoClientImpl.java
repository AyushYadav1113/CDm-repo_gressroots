package com.grassroots.cdm.integration.sectigo.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grassroots.cdm.integration.sectigo.SectigoClient;
import com.grassroots.cdm.integration.sectigo.config.SectigoProperties;
import com.grassroots.cdm.integration.sectigo.dto.SectigoCertificateDto;
import com.grassroots.cdm.integration.sectigo.dto.SectigoErrorResponse;
import com.grassroots.cdm.integration.sectigo.dto.SectigoPageResponse;
import com.grassroots.cdm.integration.sectigo.exception.SectigoAuthenticationException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoClientException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoParseException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoRateLimitException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoServerException;
import com.grassroots.cdm.integration.sectigo.exception.SectigoTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link SectigoClient} interacting with Sectigo Certificate Manager (SCM).
 * Uses Spring 6 {@link RestClient} with JdkClientHttpRequestFactory, handling custom authentication headers,
 * pagination, rate limiting, secure retries, and comprehensive error handling.
 */
@Component
@ConditionalOnProperty(name = "cdm.sectigo.mock-enabled", havingValue = "false", matchIfMissing = true)
public class SectigoClientImpl implements SectigoClient {

    private static final Logger log = LoggerFactory.getLogger(SectigoClientImpl.class);

    private final SectigoProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public SectigoClientImpl(SectigoProperties properties, ObjectMapper objectMapper) {
        this(properties, buildDefaultRestClient(properties), objectMapper);
    }

    public SectigoClientImpl(SectigoProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    private static RestClient buildDefaultRestClient(SectigoProperties props) {
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

        if (props.getBaseUrl() != null && !props.getBaseUrl().isBlank()) {
            builder.baseUrl(props.getBaseUrl());
        }

        // Sectigo SCM custom authentication headers
        if (props.getCustomerUri() != null && !props.getCustomerUri().isBlank()) {
            builder.defaultHeader("customerUri", props.getCustomerUri());
        }
        if (props.getLoginName() != null && !props.getLoginName().isBlank()) {
            builder.defaultHeader("loginName", props.getLoginName());
        }
        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            builder.defaultHeader("password", props.getPassword());
        }
        if (props.getApiToken() != null && !props.getApiToken().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiToken());
        }

        return builder.build();
    }

    @Override
    public List<SectigoCertificateDto> fetchCertificates(int size, int position, String status, String correlationId) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        String path = properties.getCertificatesPath() != null ? properties.getCertificatesPath() : "/certificates";
        int effectiveSize = size > 0 ? size : properties.getPageSize();
        int effectivePosition = Math.max(0, position);

        log.info("Fetching certificates from Sectigo SCM: path={}, size={}, position={}, status={}, correlationId={}",
                path, effectiveSize, effectivePosition, status, resolvedCorrelationId);

        return executeWithRetry(() -> {
            try {
                String responseBody = restClient.get()
                        .uri(uriBuilder -> {
                            var builder = uriBuilder.path(path)
                                    .queryParam("size", effectiveSize)
                                    .queryParam("position", effectivePosition);
                            if (status != null && !status.isBlank()) {
                                builder.queryParam("status", status);
                            }
                            return builder.build();
                        })
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
            } catch (Exception ex) {
                throw translateException(ex);
            }
        }, resolvedCorrelationId);
    }

    @Override
    public List<SectigoCertificateDto> fetchAllCertificates(String status, String correlationId) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        List<SectigoCertificateDto> allCertificates = new ArrayList<>();
        int pageSize = properties.getPageSize() > 0 ? properties.getPageSize() : 100;
        int position = 0;
        boolean hasMore = true;

        log.info("Starting complete certificate synchronization from Sectigo SCM (correlationId={})", resolvedCorrelationId);

        while (hasMore) {
            List<SectigoCertificateDto> page = fetchCertificates(pageSize, position, status, resolvedCorrelationId);
            if (page == null || page.isEmpty()) {
                hasMore = false;
            } else {
                allCertificates.addAll(page);
                if (page.size() < pageSize) {
                    hasMore = false;
                } else {
                    position += page.size();
                }
            }
        }

        log.info("Completed Sectigo certificate retrieval: totalRecords={}, correlationId={}",
                allCertificates.size(), resolvedCorrelationId);
        return allCertificates;
    }

    @Override
    public Optional<SectigoCertificateDto> fetchCertificateById(String certificateId, String correlationId) {
        if (certificateId == null || certificateId.isBlank()) {
            return Optional.empty();
        }
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        String path = (properties.getCertificatesPath() != null ? properties.getCertificatesPath() : "/certificates")
                + "/" + certificateId;

        return executeWithRetry(() -> {
            try {
                String responseBody = restClient.get()
                        .uri(path)
                        .header("X-Correlation-ID", resolvedCorrelationId)
                        .retrieve()
                        .onStatus(status -> status.value() == 404, (req, resp) -> {
                            // 404 handled as empty Optional
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
            } catch (Exception ex) {
                throw translateException(ex);
            }
        }, resolvedCorrelationId);
    }

    @Override
    public byte[] downloadCertificateChain(String certificateId, String correlationId) {
        if (certificateId == null || certificateId.isBlank()) {
            throw new SectigoClientException("Certificate ID is required to download certificate chain", 400);
        }
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        String path = (properties.getCertificatesPath() != null ? properties.getCertificatesPath() : "/certificates")
                + "/" + certificateId + "/chain";

        log.info("Downloading certificate chain for certificateId={}, correlationId={}",
                certificateId, resolvedCorrelationId);

        return executeWithRetry(() -> {
            try {
                return restClient.get()
                        .uri(path)
                        .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN, MediaType.ALL)
                        .header("X-Correlation-ID", resolvedCorrelationId)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, resp) -> {
                            handleHttpError(resp.getStatusCode().value(), resp.getBody().readAllBytes(), resp.getHeaders());
                        })
                        .body(byte[].class);
            } catch (RestClientResponseException ex) {
                handleHttpError(ex.getStatusCode().value(), ex.getResponseBodyAsByteArray(), ex.getResponseHeaders());
                return new byte[0];
            } catch (Exception ex) {
                throw translateException(ex);
            }
        }, resolvedCorrelationId);
    }

    private List<SectigoCertificateDto> parseCertificateListResponse(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) {
                return objectMapper.readValue(trimmed, new TypeReference<List<SectigoCertificateDto>>() {});
            }

            // Wrapped envelope { "total": 10, "certificates": [ ... ] }
            SectigoPageResponse<SectigoCertificateDto> envelope = objectMapper.readValue(
                    trimmed, new TypeReference<>() {});
            if (envelope != null && envelope.getCertificates() != null) {
                return envelope.getCertificates();
            }

            return Collections.emptyList();
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse Sectigo certificate list response: {}", json, ex);
            throw new SectigoParseException("Malformed JSON response from Sectigo: " + ex.getMessage(), ex);
        }
    }

    private Optional<SectigoCertificateDto> parseSingleCertificateResponse(String json) {
        try {
            SectigoCertificateDto dto = objectMapper.readValue(json, SectigoCertificateDto.class);
            return Optional.ofNullable(dto);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse Sectigo single certificate response: {}", json, ex);
            throw new SectigoParseException("Malformed JSON response from Sectigo: " + ex.getMessage(), ex);
        }
    }

    private void handleHttpError(int statusCode, byte[] responseBodyBytes, HttpHeaders headers) {
        String responseBody = responseBodyBytes != null ? new String(responseBodyBytes, StandardCharsets.UTF_8) : "";
        String errorMessage = extractErrorMessage(responseBody);

        log.warn("Sectigo API returned HTTP error: status={}, error={}", statusCode, errorMessage);

        if (statusCode == 401 || statusCode == 403) {
            throw new SectigoAuthenticationException("Sectigo authentication failed: " + errorMessage, statusCode);
        } else if (statusCode == 429) {
            Long retryAfter = parseRetryAfter(headers);
            throw new SectigoRateLimitException("Sectigo rate limit exceeded: " + errorMessage, retryAfter);
        } else if (statusCode >= 400 && statusCode < 500) {
            throw new SectigoClientException("Sectigo client error: " + errorMessage, statusCode);
        } else if (statusCode >= 500) {
            throw new SectigoServerException("Sectigo server error: " + errorMessage, statusCode);
        } else {
            throw new SectigoException("Sectigo unexpected error: " + errorMessage, statusCode);
        }
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "No response body provided";
        }
        try {
            SectigoErrorResponse errorResponse = objectMapper.readValue(body, SectigoErrorResponse.class);
            if (errorResponse != null) {
                return errorResponse.getFormattedMessage();
            }
        } catch (Exception ignored) {
            // Not Sectigo error schema, return raw body truncated
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

    private SectigoException translateException(Exception ex) {
        if (ex instanceof SectigoException se) {
            return se;
        }
        if (isTimeout(ex)) {
            return new SectigoTimeoutException("Sectigo connection or socket timed out: " + ex.getMessage(), ex);
        }
        if (ex instanceof ResourceAccessException rae) {
            return new SectigoServerException("Sectigo unreachable: " + rae.getMessage(), 503, rae);
        }
        return new SectigoParseException("Failed processing Sectigo response: " + ex.getMessage(), ex);
    }

    private boolean isTimeout(Throwable t) {
        if (t == null) {
            return false;
        }
        if (t instanceof SocketTimeoutException
                || t instanceof java.net.http.HttpTimeoutException
                || t instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        String msg = t.getMessage() != null ? t.getMessage().toLowerCase() : "";
        if (msg.contains("timed out") || msg.contains("timeout") || msg.contains("time out")) {
            return true;
        }
        return isTimeout(t.getCause());
    }

    private <T> T executeWithRetry(RetryableOperation<T> operation, String correlationId) {
        int attempts = 0;
        int maxAttempts = Math.max(1, properties.getMaxRetries());
        long backoff = Math.max(100, properties.getBackoffMs());

        while (true) {
            attempts++;
            try {
                return operation.execute();
            } catch (SectigoAuthenticationException | SectigoClientException | SectigoParseException ex) {
                // Non-retryable errors
                throw ex;
            } catch (SectigoRateLimitException ex) {
                if (attempts >= maxAttempts) {
                    throw ex;
                }
                long waitTime = ex.getRetryAfterSeconds() != null ? ex.getRetryAfterSeconds() * 1000 : backoff * attempts;
                log.warn("Rate limited by Sectigo (attempt {}/{}). Waiting {}ms before retry...", attempts, maxAttempts, waitTime);
                sleep(waitTime);
            } catch (SectigoTimeoutException | SectigoServerException ex) {
                if (attempts >= maxAttempts) {
                    throw ex;
                }
                long waitTime = backoff * (1L << (attempts - 1)); // exponential backoff
                log.warn("Retryable error from Sectigo (attempt {}/{}): {}. Retrying in {}ms...",
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
            throw new SectigoException("Operation interrupted during retry backoff", ex);
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
