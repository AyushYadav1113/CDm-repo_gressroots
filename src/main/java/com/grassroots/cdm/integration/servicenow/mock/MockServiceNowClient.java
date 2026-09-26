package com.grassroots.cdm.integration.servicenow.mock;

import com.grassroots.cdm.integration.servicenow.ServiceNowClient;
import com.grassroots.cdm.integration.servicenow.dto.ServiceNowCertificateDto;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowAuthenticationException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowParseException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowRateLimitException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowServerException;
import com.grassroots.cdm.integration.servicenow.exception.ServiceNowTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Mock implementation of {@link ServiceNowClient} for local testing and offline execution.
 */
@Component
@ConditionalOnProperty(name = "cdm.servicenow.mock-enabled", havingValue = "true")
public class MockServiceNowClient implements ServiceNowClient {

    private static final Logger log = LoggerFactory.getLogger(MockServiceNowClient.class);

    private final Map<String, ServiceNowCertificateDto> certificates = new ConcurrentHashMap<>();
    private volatile boolean simulateTimeout = false;
    private volatile boolean simulateAuthFailure = false;
    private volatile boolean simulateUnavailable = false;
    private volatile boolean simulateRateLimit = false;
    private volatile boolean simulateMalformedResponse = false;

    public void addCertificate(ServiceNowCertificateDto cert) {
        if (cert != null && cert.getSysId() != null) {
            certificates.put(cert.getSysId(), cert);
        }
    }

    public void clear() {
        certificates.clear();
        resetSimulations();
    }

    public void resetSimulations() {
        simulateTimeout = false;
        simulateAuthFailure = false;
        simulateUnavailable = false;
        simulateRateLimit = false;
        simulateMalformedResponse = false;
    }

    public void setSimulateTimeout(boolean simulateTimeout) {
        this.simulateTimeout = simulateTimeout;
    }

    public void setSimulateAuthFailure(boolean simulateAuthFailure) {
        this.simulateAuthFailure = simulateAuthFailure;
    }

    public void setSimulateUnavailable(boolean simulateUnavailable) {
        this.simulateUnavailable = simulateUnavailable;
    }

    public void setSimulateRateLimit(boolean simulateRateLimit) {
        this.simulateRateLimit = simulateRateLimit;
    }

    public void setSimulateMalformedResponse(boolean simulateMalformedResponse) {
        this.simulateMalformedResponse = simulateMalformedResponse;
    }

    @Override
    public List<ServiceNowCertificateDto> fetchCertificates(String queryFilter, int limit, int offset, String correlationId) {
        checkSimulatedFailures();
        List<ServiceNowCertificateDto> list = new ArrayList<>(certificates.values());
        int fromIndex = Math.min(offset, list.size());
        int toIndex = Math.min(fromIndex + limit, list.size());
        log.info("[MockServiceNowClient] fetchCertificates: returning {} records (offset={}, limit={})",
                toIndex - fromIndex, offset, limit);
        return new ArrayList<>(list.subList(fromIndex, toIndex));
    }

    @Override
    public List<ServiceNowCertificateDto> fetchAllCertificates(String correlationId) {
        checkSimulatedFailures();
        log.info("[MockServiceNowClient] fetchAllCertificates: returning {} records", certificates.size());
        return new ArrayList<>(certificates.values());
    }

    @Override
    public Optional<ServiceNowCertificateDto> fetchCertificateBySysId(String sysId, String correlationId) {
        checkSimulatedFailures();
        return Optional.ofNullable(certificates.get(sysId));
    }

    @Override
    public void updateCertificateStatus(String sysId, String status, String correlationId) {
        checkSimulatedFailures();
        ServiceNowCertificateDto cert = certificates.get(sysId);
        if (cert != null) {
            cert.setOperationalStatus(status);
            cert.setState(status);
            log.info("[MockServiceNowClient] Updated sys_id={} status to {}", sysId, status);
        }
    }

    private void checkSimulatedFailures() {
        if (simulateTimeout) {
            throw new ServiceNowTimeoutException("Mocked connection read timeout communicating with ServiceNow");
        }
        if (simulateAuthFailure) {
            throw new ServiceNowAuthenticationException("Mocked 401 Unauthorized: Invalid credentials", 401);
        }
        if (simulateUnavailable) {
            throw new ServiceNowServerException("Mocked 503 Service Unavailable: ServiceNow node down", 503);
        }
        if (simulateRateLimit) {
            throw new ServiceNowRateLimitException("Mocked 429 Too Many Requests: Rate limit exceeded", 1L);
        }
        if (simulateMalformedResponse) {
            throw new ServiceNowParseException("Mocked JSON syntax error: Unexpected token");
        }
    }
}
