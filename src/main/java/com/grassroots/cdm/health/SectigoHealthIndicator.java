package com.grassroots.cdm.health;

import com.grassroots.cdm.integration.sectigo.SectigoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * HealthIndicator verifying connectivity to Sectigo Certificate Manager (SCM) REST API without leaking customer URIs or tokens.
 */
@Component
public class SectigoHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(SectigoHealthIndicator.class);

    private final SectigoClient sectigoClient;

    public SectigoHealthIndicator(@Autowired(required = false) SectigoClient sectigoClient) {
        this.sectigoClient = sectigoClient;
    }

    @Override
    public Health health() {
        if (sectigoClient == null) {
            return Health.unknown()
                    .withDetail("service", "Sectigo SCM")
                    .withDetail("status", "NOT_CONFIGURED")
                    .build();
        }

        try {
            // Lightweight probe requesting up to 1 certificate to verify connectivity and authentication
            sectigoClient.fetchCertificates(1, 0, "ISSUED", "health-check");
            return Health.up()
                    .withDetail("service", "Sectigo SCM")
                    .withDetail("status", "REACHABLE")
                    .build();
        } catch (Exception ex) {
            log.warn("Sectigo health check probe failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("service", "Sectigo SCM")
                    .withDetail("status", "UNAVAILABLE")
                    .withDetail("error", "Sectigo probe failed")
                    .build();
        }
    }
}
