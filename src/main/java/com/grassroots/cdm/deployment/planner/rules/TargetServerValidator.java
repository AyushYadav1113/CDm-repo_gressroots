package com.grassroots.cdm.deployment.planner.rules;

import com.grassroots.cdm.deployment.planner.exception.MissingServerException;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.ServerStatus;
import org.springframework.stereotype.Component;

/**
 * Validates target server existence and operational readiness for certificate deployment.
 */
@Component
public class TargetServerValidator {

    /**
     * Validates that the target server is present and eligible to accept deployments.
     *
     * @param installation the installation target
     * @return the validated TargetServer
     * @throws MissingServerException if server is null, missing, or decommissioned
     */
    public TargetServer validate(CertificateInstallation installation) {
        if (installation == null) {
            throw new IllegalArgumentException("CertificateInstallation must not be null");
        }

        TargetServer server = installation.getServer();
        if (server == null) {
            throw new MissingServerException("Target server is missing or null for installation: " + installation.getId());
        }

        if (server.getHostname() == null || server.getHostname().trim().isEmpty()) {
            throw new MissingServerException(server.getId(), "Target server has blank hostname for installation: " + installation.getId());
        }

        if (server.getStatus() == ServerStatus.DECOMMISSIONED) {
            throw new MissingServerException(
                    server.getId(),
                    "Target server '" + server.getHostname() + "' is DECOMMISSIONED and cannot accept deployments."
            );
        }

        return server;
    }
}
