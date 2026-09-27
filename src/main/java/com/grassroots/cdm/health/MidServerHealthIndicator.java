package com.grassroots.cdm.health;

import com.grassroots.cdm.integration.midserver.MidServerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * HealthIndicator verifying connectivity to ServiceNow MID Server cluster without exposing network topology or tokens.
 */
@Component
public class MidServerHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(MidServerHealthIndicator.class);

    private final MidServerClient midServerClient;

    public MidServerHealthIndicator(@Autowired(required = false) MidServerClient midServerClient) {
        this.midServerClient = midServerClient;
    }

    @Override
    public Health health() {
        if (midServerClient == null) {
            return Health.unknown()
                    .withDetail("service", "MID Server")
                    .withDetail("status", "NOT_CONFIGURED")
                    .build();
        }

        try {
            boolean healthy = midServerClient.checkHealth();
            if (healthy) {
                return Health.up()
                        .withDetail("service", "MID Server")
                        .withDetail("status", "REACHABLE")
                        .build();
            } else {
                return Health.down()
                        .withDetail("service", "MID Server")
                        .withDetail("status", "UNAVAILABLE")
                        .build();
            }
        } catch (Exception ex) {
            log.warn("MID Server health check probe failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("service", "MID Server")
                    .withDetail("status", "UNAVAILABLE")
                    .withDetail("error", "MID Server probe failed")
                    .build();
        }
    }
}
