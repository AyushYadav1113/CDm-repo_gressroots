package com.grassroots.cdm.deployment.planner.rules;

import com.grassroots.cdm.deployment.planner.config.DeploymentPlannerProperties;
import com.grassroots.cdm.deployment.planner.exception.MissingMidServerException;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import org.springframework.stereotype.Component;

/**
 * Validates that the target server has an assigned, operational ServiceNow MID Server.
 */
@Component
public class MidServerValidator {

    private final DeploymentPlannerProperties properties;

    public MidServerValidator(DeploymentPlannerProperties properties) {
        this.properties = properties;
    }

    /**
     * Validates that the target server has a suitable, operational MID Server.
     *
     * @param server the target server
     * @return the operational MidServer
     * @throws MissingMidServerException if MID server is null, down, or non-operational
     */
    public MidServer validate(TargetServer server) {
        if (server == null) {
            throw new IllegalArgumentException("TargetServer must not be null");
        }

        MidServer midServer = server.getMidServer();
        if (midServer == null) {
            throw new MissingMidServerException("Target server '" + server.getHostname() + "' has no assigned MID Server.");
        }

        if (properties.isRequireActiveMidServer()) {
            if (midServer.getStatus() == null || midServer.getStatus() != MidServerStatus.UP) {
                throw new MissingMidServerException(
                        "MID Server '" + midServer.getName() + "' for target server '" + server.getHostname()
                                + "' is not operational (status: " + midServer.getStatus() + ")."
                );
            }
        }

        return midServer;
    }
}
