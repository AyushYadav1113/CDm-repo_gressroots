package com.grassroots.cdm.deployment.planner.rules;

import com.grassroots.cdm.deployment.TargetType;
import com.grassroots.cdm.deployment.planner.exception.UnsupportedTechnologyException;
import com.grassroots.cdm.entity.enums.DeploymentType;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import org.springframework.stereotype.Component;

/**
 * Resolves deployment type and target type based on target server and installation technology.
 */
@Component
public class DeploymentTypeResolver {

    public record ResolvedDeploymentType(
            DeploymentType deploymentType,
            String targetType,
            TargetType genericTargetType
    ) {}

    /**
     * Resolves the appropriate deployment type from the given ServerTechnology.
     *
     * @param technology the server or installation technology
     * @return ResolvedDeploymentType containing deploymentType and targetType
     * @throws UnsupportedTechnologyException if the technology is null or unsupported
     */
    public ResolvedDeploymentType resolve(ServerTechnology technology) {
        if (technology == null) {
            throw new UnsupportedTechnologyException("null", "Target technology is null and cannot be resolved.");
        }

        return switch (technology) {
            case IIS -> new ResolvedDeploymentType(
                    DeploymentType.IIS,
                    "IIS",
                    TargetType.WINDOWS_IIS
            );
            case APACHE -> new ResolvedDeploymentType(
                    DeploymentType.APACHE,
                    "APACHE",
                    TargetType.LINUX_APACHE
            );
            case NGINX -> new ResolvedDeploymentType(
                    DeploymentType.NGINX,
                    "NGINX",
                    TargetType.LINUX_NGINX
            );
            case JAVA_KEYSTORE, TOMCAT, WEBLOGIC, WEBSPHERE -> new ResolvedDeploymentType(
                    DeploymentType.JAVA,
                    "JAVA",
                    TargetType.JAVA_KEYSTORE
            );
            default -> throw new UnsupportedTechnologyException(
                    technology.name(),
                    "Technology " + technology.name() + " is currently unsupported for automated deployment."
            );
        };
    }

    /**
     * Checks if a technology is supported for automated deployment.
     *
     * @param technology the technology to check
     * @return true if supported, false otherwise
     */
    public boolean isSupported(ServerTechnology technology) {
        if (technology == null) {
            return false;
        }
        return switch (technology) {
            case IIS, APACHE, NGINX, JAVA_KEYSTORE, TOMCAT, WEBLOGIC, WEBSPHERE -> true;
            default -> false;
        };
    }
}
