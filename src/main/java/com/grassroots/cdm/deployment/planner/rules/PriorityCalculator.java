package com.grassroots.cdm.deployment.planner.rules;

import com.grassroots.cdm.deployment.planner.config.DeploymentPlannerProperties;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.JobPriority;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Deterministically evaluates certificate expiration urgency and target server environment
 * to calculate deployment job priority.
 */
@Component
public class PriorityCalculator {

    private final DeploymentPlannerProperties properties;

    public PriorityCalculator(DeploymentPlannerProperties properties) {
        this.properties = properties;
    }

    /**
     * Calculates job priority based on validity expiration and server environment.
     *
     * @param oldCert the expiring certificate
     * @param server  the target server
     * @return JobPriority (CRITICAL, HIGH, NORMAL, LOW)
     */
    public JobPriority calculatePriority(CertificateRecord oldCert, TargetServer server) {
        return calculatePriority(oldCert, server, Instant.now());
    }

    /**
     * Overloaded method allowing clock injection for deterministic testing.
     */
    public JobPriority calculatePriority(CertificateRecord oldCert, TargetServer server, Instant now) {
        if (oldCert != null && oldCert.getValidTo() != null) {
            Instant validTo = oldCert.getValidTo();
            Duration remaining = Duration.between(now, validTo);
            long daysRemaining = remaining.toDays();

            // Expired or expiring within critical threshold (e.g. <= 7 days)
            if (daysRemaining <= properties.getCriticalThresholdDays()) {
                return JobPriority.CRITICAL;
            }

            // Expiring within high threshold (e.g. <= 15 days)
            if (daysRemaining <= properties.getHighThresholdDays()) {
                return JobPriority.HIGH;
            }
        }

        // For non-urgent renewals, determine priority by environment tier
        if (server != null && server.getEnvironment() != null) {
            EnvironmentType env = server.getEnvironment();
            return switch (env) {
                case PRODUCTION, DISASTER_RECOVERY -> JobPriority.HIGH;
                case STAGING -> JobPriority.NORMAL;
                case QA, DEVELOPMENT -> JobPriority.LOW;
                default -> JobPriority.NORMAL;
            };
        }

        return JobPriority.NORMAL;
    }
}
