package com.grassroots.cdm.deployment.adapter.java.profile;

import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves a concrete JavaDeploymentProfile from a DeploymentJob and its installation metadata.
 *
 * Supports flexible configuration across diverse Java application runtimes:
 * - Spring Boot
 * - Apache Tomcat
 * - Oracle WebLogic
 * - IBM WebSphere
 * - Custom Java microservices
 */
@Component
public class JavaDeploymentProfileResolver {

    public JavaDeploymentProfile resolveProfile(DeploymentJob job) {
        if (job == null) {
            throw new IllegalArgumentException("DeploymentJob cannot be null");
        }

        CertificateInstallation installation = job.getInstallation();
        TargetServer targetServer = job.getTargetServer();
        boolean isWindows = targetServer != null && targetServer.getOperatingSystem() == ServerOperatingSystem.WINDOWS_SERVER;

        String bindingInfo = installation != null ? installation.getBindingInfo() : null;
        String installPath = installation != null ? installation.getInstallationPath() : null;
        int port = job.getTargetPort() > 0 ? job.getTargetPort() : 8443;

        Map<String, String> parsedProperties = parseBindingProperties(bindingInfo);

        // Alias resolution
        String alias = parsedProperties.getOrDefault("alias",
                (bindingInfo != null && !bindingInfo.contains("=") && !bindingInfo.isBlank())
                        ? bindingInfo.trim()
                        : (job.getNewCertificate() != null && job.getNewCertificate().getCommonName() != null
                        ? sanitizeAlias(job.getNewCertificate().getCommonName())
                        : "server"));

        // Location resolution
        String keystoreLocation = parsedProperties.get("keystoreLocation");
        if (keystoreLocation == null || keystoreLocation.isBlank()) {
            if (installPath != null && !installPath.isBlank()
                    && (installPath.endsWith(".p12") || installPath.endsWith(".jks") || installPath.endsWith(".keystore"))) {
                keystoreLocation = installPath.trim();
            } else {
                keystoreLocation = isWindows
                        ? "C:\\app\\security\\" + alias + ".p12"
                        : "/var/lib/cdm/keystores/" + alias + ".p12";
            }
        }

        // Keystore Type resolution
        String typeStr = parsedProperties.get("type");
        KeystoreType type = typeStr != null ? KeystoreType.fromString(typeStr) : KeystoreType.fromLocation(keystoreLocation);

        // Restart Strategy resolution
        String strategyStr = parsedProperties.get("restartStrategy");
        RestartStrategy restartStrategy = strategyStr != null ? RestartStrategy.fromString(strategyStr) : RestartStrategy.SYSTEMD_SERVICE;

        // Service name resolution
        String serviceName = parsedProperties.getOrDefault("serviceName",
                (targetServer != null && targetServer.getTechnology() == ServerTechnology.TOMCAT) ? "tomcat" : alias);

        // Configuration reference resolution
        String appConfigLocation = parsedProperties.get("appConfigLocation");
        if (appConfigLocation == null && targetServer != null && targetServer.getTechnology() == ServerTechnology.TOMCAT) {
            appConfigLocation = isWindows ? "C:\\tomcat\\conf\\server.xml" : "/etc/tomcat/server.xml";
        }

        // Security Vault Locators (Never store plaintext passwords)
        String targetThumbprint = job.getNewCertificate() != null ? job.getNewCertificate().getThumbprint() : "default";
        String keystorePasswordVaultRef = parsedProperties.getOrDefault("keystorePasswordVaultRef",
                "cyberark://GrassrootsSafe/Account/JavaKeystore_" + alias);
        String keyPasswordVaultRef = parsedProperties.get("keyPasswordVaultRef");

        // POSIX file permissions
        String fileMode = parsedProperties.getOrDefault("fileMode", "0640");
        String owner = parsedProperties.getOrDefault("owner", "root");
        String group = parsedProperties.getOrDefault("group", "root");

        return JavaDeploymentProfile.builder()
                .profileName(alias + "-profile")
                .keystoreType(type)
                .keystoreLocation(keystoreLocation)
                .keyAlias(alias)
                .keystorePasswordVaultRef(keystorePasswordVaultRef)
                .keyPasswordVaultRef(keyPasswordVaultRef)
                .appConfigLocation(appConfigLocation)
                .restartStrategy(restartStrategy)
                .serviceName(serviceName)
                .targetPort(port)
                .fileMode(fileMode)
                .owner(owner)
                .group(group)
                .healthCheckEndpoint(parsedProperties.getOrDefault("healthCheckEndpoint", "/actuator/health"))
                .build();
    }

    private Map<String, String> parseBindingProperties(String bindingInfo) {
        Map<String, String> map = new HashMap<>();
        if (bindingInfo == null || bindingInfo.isBlank()) {
            return map;
        }

        // Support semi-colon or comma delimited key=value configuration
        String[] pairs = bindingInfo.split("[;,]");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = pair.substring(0, eq).trim();
                String val = pair.substring(eq + 1).trim();
                if (!key.isEmpty() && !val.isEmpty()) {
                    map.put(key, val);
                }
            }
        }
        return map;
    }

    private String sanitizeAlias(String commonName) {
        return commonName.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
