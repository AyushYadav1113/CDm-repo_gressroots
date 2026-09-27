package com.grassroots.cdm.credential.cyberark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grassroots.cdm.credential.dto.CyberArkAccountResponse;
import com.grassroots.cdm.credential.exception.CredentialException;
import com.grassroots.cdm.credential.exception.CredentialExpiredException;
import com.grassroots.cdm.credential.exception.CredentialNotFoundException;
import com.grassroots.cdm.credential.exception.CredentialRotationInProgressException;
import com.grassroots.cdm.credential.exception.CyberArkUnavailableException;
import com.grassroots.cdm.credential.exception.CyberArkUnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Production implementation of CyberArkClient using Spring 6 RestClient over TLS.
 * Connects to CyberArk Central Credential Provider (CCP) /AIMWebService/api/Accounts.
 */
@Component
public class DefaultCyberArkClient implements CyberArkClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultCyberArkClient.class);

    private final CyberArkProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public DefaultCyberArkClient(CyberArkProperties properties, ObjectMapper objectMapper) {
        this(properties, buildRestClient(properties), objectMapper);
    }

    public DefaultCyberArkClient(CyberArkProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    private static RestClient buildRestClient(CyberArkProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public CyberArkAccountResponse getAccount(String safe, String account, String targetHost) {
        if (safe == null || safe.isBlank()) {
            throw new IllegalArgumentException("Safe identifier cannot be null or blank");
        }
        if (account == null || account.isBlank()) {
            throw new IllegalArgumentException("Account identifier cannot be null or blank");
        }

        String url = buildAccountUrl(safe, account, targetHost);
        log.info("Requesting credential from CyberArk CCP for Safe '{}', Account '{}', Host '{}'",
                safe, account, targetHost != null ? targetHost : "[ANY]");

        int attempts = 0;
        int maxAttempts = Math.max(1, properties.getMaxRetries());

        while (true) {
            attempts++;
            try {
                CyberArkAccountResponse response = restClient.get()
                        .uri(URI.create(url))
                        .retrieve()
                        .body(CyberArkAccountResponse.class);

                if (response == null) {
                    throw new CredentialNotFoundException("CyberArk returned empty response body", safe, account);
                }

                // Check for CPM password rotation in progress
                if (response.isPasswordChangeInProcess()) {
                    log.warn("CyberArk CPM password rotation currently in progress for Safe '{}', Account '{}' (attempt {}/{})",
                            safe, account, attempts, maxAttempts);
                    if (attempts < maxAttempts) {
                        sleepBackoff();
                        continue;
                    }
                    throw new CredentialRotationInProgressException(safe, account);
                }

                // Check for credential expiration
                if (response.isExpired()) {
                    log.warn("CyberArk credential is past expiration policy for Safe '{}', Account '{}'", safe, account);
                    throw new CredentialExpiredException(safe, account, "Password marked expired in CyberArk vault");
                }

                log.info("Successfully retrieved transient credential from CyberArk for Safe '{}', Account '{}'", safe, account);
                return response;

            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                String body = ex.getResponseBodyAsString();

                log.warn("CyberArk CCP returned HTTP {} for Safe '{}', Account '{}': {}",
                        status, safe, account, sanitizeResponseBody(body));

                if (status == 401 || status == 403) {
                    throw new CyberArkUnauthorizedException(
                            "CyberArk access denied: " + sanitizeResponseBody(body),
                            properties.getAppId(), safe, status);
                }

                if (status == 404) {
                    throw new CredentialNotFoundException(safe, account);
                }

                if (status == 409 || (body != null && body.contains("APPAP314E"))) {
                    log.warn("CyberArk reported password change in progress (HTTP 409) for Safe '{}', Account '{}'", safe, account);
                    if (attempts < maxAttempts) {
                        sleepBackoff();
                        continue;
                    }
                    throw new CredentialRotationInProgressException(safe, account);
                }

                if (status >= 500) {
                    if (attempts < maxAttempts) {
                        log.warn("CyberArk 5xx transient error (HTTP {}), retrying (attempt {}/{})", status, attempts, maxAttempts);
                        sleepBackoff();
                        continue;
                    }
                    throw new CyberArkUnavailableException(
                            "CyberArk server error (HTTP " + status + "): " + sanitizeResponseBody(body),
                            properties.getBaseUrl(), ex);
                }

                throw new CredentialException("CyberArk returned HTTP " + status + ": " + sanitizeResponseBody(body),
                        safe, account, ex);

            } catch (ResourceAccessException ex) {
                log.warn("Network or timeout error contacting CyberArk at {} (attempt {}/{}): {}",
                        properties.getBaseUrl(), attempts, maxAttempts, ex.getMessage());
                if (attempts < maxAttempts) {
                    sleepBackoff();
                    continue;
                }
                throw new CyberArkUnavailableException("CyberArk service unreachable or timed out: " + ex.getMessage(),
                        properties.getBaseUrl(), ex);
            }
        }
    }

    @Override
    public boolean ping() {
        try {
            // Test connectivity to CyberArk CCP base endpoint
            restClient.get()
                    .uri(URI.create(properties.getBaseUrl()))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            log.debug("CyberArk ping probe failed: {}", ex.getMessage());
            return false;
        }
    }

    private String buildAccountUrl(String safe, String account, String targetHost) {
        String base = properties.getBaseUrl();
        if (!base.endsWith("/api/Accounts") && !base.endsWith("/Accounts")) {
            if (base.endsWith("/")) {
                base = base + "api/Accounts";
            } else {
                base = base + "/api/Accounts";
            }
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(base)
                .queryParam("AppID", properties.getAppId())
                .queryParam("Safe", safe)
                .queryParam("Object", account);

        if (targetHost != null && !targetHost.isBlank()) {
            builder.queryParam("Address", targetHost);
        }

        return builder.toUriString();
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(Math.max(100, properties.getRetryBackoffMs()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String sanitizeResponseBody(String body) {
        if (body == null) return "";
        // Prevent accidental reflection of passwords if error JSON contains user inputs
        return body.replaceAll("(?i)(\"Content\"\\s*:\\s*\")[^\"]+(\")", "$1[REDACTED]$2");
    }
}
