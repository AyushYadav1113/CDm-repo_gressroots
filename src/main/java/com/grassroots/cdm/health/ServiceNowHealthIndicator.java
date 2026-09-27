package com.grassroots.cdm.health;

import com.grassroots.cdm.integration.servicenow.ServiceNowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * HealthIndicator verifying connectivity to ServiceNow CMDB API without leaking credentials or internal URLs.
 */
@Component
public class ServiceNowHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(ServiceNowHealthIndicator.class);

    private final ServiceNowClient serviceNowClient;

    public ServiceNowHealthIndicator(@Autowired(required = false) ServiceNowClient serviceNowClient) {
        this.serviceNowClient = serviceNowClient;
    }

    @Override
    public Health health() {
        if (serviceNowClient == null) {
            return Health.unknown()
                    .withDetail("service", "ServiceNow CMDB")
                    .withDetail("status", "NOT_CONFIGURED")
                    .build();
        }

        try {
            // Lightweight probe fetching up to 1 record to confirm network and authentication health
            serviceNowClient.fetchCertificates("sys_idISNOTEMPTY", 1, 0, "health-check");
            return Health.up()
                    .withDetail("service", "ServiceNow CMDB")
                    .withDetail("status", "REACHABLE")
                    .build();
        } catch (Exception ex) {
            log.warn("ServiceNow health check probe failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("service", "ServiceNow CMDB")
                    .withDetail("status", "UNAVAILABLE")
                    .withDetail("error", "ServiceNow probe failed")
                    .build();
        }
    }
}
