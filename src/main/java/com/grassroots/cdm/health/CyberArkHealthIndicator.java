package com.grassroots.cdm.health;

import com.grassroots.cdm.credential.cyberark.CyberArkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * HealthIndicator verifying connectivity to CyberArk Central Credential Provider (CCP) without exposing AppID or safe details.
 */
@Component
public class CyberArkHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(CyberArkHealthIndicator.class);

    private final CyberArkClient cyberArkClient;

    public CyberArkHealthIndicator(@Autowired(required = false) CyberArkClient cyberArkClient) {
        this.cyberArkClient = cyberArkClient;
    }

    @Override
    public Health health() {
        if (cyberArkClient == null) {
            return Health.unknown()
                    .withDetail("service", "CyberArk CCP")
                    .withDetail("status", "NOT_CONFIGURED")
                    .build();
        }

        try {
            boolean available = cyberArkClient.ping();
            if (available) {
                return Health.up()
                        .withDetail("service", "CyberArk CCP")
                        .withDetail("status", "REACHABLE")
                        .build();
            } else {
                return Health.down()
                        .withDetail("service", "CyberArk CCP")
                        .withDetail("status", "UNAVAILABLE")
                        .build();
            }
        } catch (Exception ex) {
            log.warn("CyberArk health check probe failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("service", "CyberArk CCP")
                    .withDetail("status", "UNAVAILABLE")
                    .withDetail("error", "CyberArk probe failed")
                    .build();
        }
    }
}
