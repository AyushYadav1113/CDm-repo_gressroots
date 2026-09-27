package com.grassroots.cdm.deployment.adapter.java.profile;

import java.util.Objects;

/**
 * Deployment profile / configuration abstraction for heterogeneous Java applications.
 *
 * Defines:
 * - keystore type (JKS or PKCS12)
 * - keystore location
 * - certificate & private key alias
 * - application configuration reference
 * - restart strategy
 *
 * Passwords and secrets are strictly referenced via vault locators and never stored in source code
 * or emitted in logs.
 */
public class JavaDeploymentProfile {

    private final String profileName;
    private final KeystoreType keystoreType;
    private final String keystoreLocation;
    private final String keyAlias;
    private final String keystorePasswordVaultRef;
    private final String keyPasswordVaultRef;
    private final String appConfigLocation;
    private final RestartStrategy restartStrategy;
    private final String serviceName;
    private final String restartScriptPath;
    private final int targetPort;
    private final String fileMode;
    private final String owner;
    private final String group;
    private final String backupKeystoreLocation;
    private final String healthCheckEndpoint;

    private JavaDeploymentProfile(Builder builder) {
        this.profileName = builder.profileName != null ? builder.profileName : "default-java-profile";
        this.keystoreType = builder.keystoreType != null ? builder.keystoreType : KeystoreType.PKCS12;
        this.keystoreLocation = Objects.requireNonNull(builder.keystoreLocation, "keystoreLocation cannot be null");
        this.keyAlias = Objects.requireNonNull(builder.keyAlias, "keyAlias cannot be null");
        this.keystorePasswordVaultRef = builder.keystorePasswordVaultRef;
        this.keyPasswordVaultRef = builder.keyPasswordVaultRef;
        this.appConfigLocation = builder.appConfigLocation;
        this.restartStrategy = builder.restartStrategy != null ? builder.restartStrategy : RestartStrategy.SYSTEMD_SERVICE;
        this.serviceName = builder.serviceName;
        this.restartScriptPath = builder.restartScriptPath;
        this.targetPort = builder.targetPort > 0 ? builder.targetPort : 8443;
        this.fileMode = builder.fileMode != null ? builder.fileMode : "0640";
        this.owner = builder.owner != null ? builder.owner : "root";
        this.group = builder.group != null ? builder.group : "root";
        this.backupKeystoreLocation = builder.backupKeystoreLocation != null
                ? builder.backupKeystoreLocation
                : this.keystoreLocation + ".cdm-bak";
        this.healthCheckEndpoint = builder.healthCheckEndpoint != null ? builder.healthCheckEndpoint : "/actuator/health";
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getProfileName() {
        return profileName;
    }

    public KeystoreType getKeystoreType() {
        return keystoreType;
    }

    public String getKeystoreLocation() {
        return keystoreLocation;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public String getKeystorePasswordVaultRef() {
        return keystorePasswordVaultRef;
    }

    public String getKeyPasswordVaultRef() {
        return keyPasswordVaultRef;
    }

    public String getAppConfigLocation() {
        return appConfigLocation;
    }

    public RestartStrategy getRestartStrategy() {
        return restartStrategy;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getRestartScriptPath() {
        return restartScriptPath;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public String getFileMode() {
        return fileMode;
    }

    public String getOwner() {
        return owner;
    }

    public String getGroup() {
        return group;
    }

    public String getBackupKeystoreLocation() {
        return backupKeystoreLocation;
    }

    public String getHealthCheckEndpoint() {
        return healthCheckEndpoint;
    }

    public boolean isSecurePermissions() {
        return "0600".equals(fileMode) || "0640".equals(fileMode);
    }

    @Override
    public String toString() {
        return "JavaDeploymentProfile{" +
                "profileName='" + profileName + '\'' +
                ", keystoreType=" + keystoreType +
                ", keystoreLocation='" + keystoreLocation + '\'' +
                ", keyAlias='" + keyAlias + '\'' +
                ", appConfigLocation='" + appConfigLocation + '\'' +
                ", restartStrategy=" + restartStrategy +
                ", serviceName='" + serviceName + '\'' +
                ", targetPort=" + targetPort +
                ", fileMode='" + fileMode + '\'' +
                ", owner='" + owner + '\'' +
                ", group='" + group + '\'' +
                ", keystorePasswordVaultRef='[REDACTED]'" +
                ", keyPasswordVaultRef='[REDACTED]'" +
                '}';
    }

    public static class Builder {
        private String profileName;
        private KeystoreType keystoreType;
        private String keystoreLocation;
        private String keyAlias;
        private String keystorePasswordVaultRef;
        private String keyPasswordVaultRef;
        private String appConfigLocation;
        private RestartStrategy restartStrategy;
        private String serviceName;
        private String restartScriptPath;
        private int targetPort = 8443;
        private String fileMode = "0640";
        private String owner = "root";
        private String group = "root";
        private String backupKeystoreLocation;
        private String healthCheckEndpoint = "/actuator/health";

        public Builder profileName(String profileName) {
            this.profileName = profileName;
            return this;
        }

        public Builder keystoreType(KeystoreType keystoreType) {
            this.keystoreType = keystoreType;
            return this;
        }

        public Builder keystoreLocation(String keystoreLocation) {
            this.keystoreLocation = keystoreLocation;
            return this;
        }

        public Builder keyAlias(String keyAlias) {
            this.keyAlias = keyAlias;
            return this;
        }

        public Builder keystorePasswordVaultRef(String keystorePasswordVaultRef) {
            this.keystorePasswordVaultRef = keystorePasswordVaultRef;
            return this;
        }

        public Builder keyPasswordVaultRef(String keyPasswordVaultRef) {
            this.keyPasswordVaultRef = keyPasswordVaultRef;
            return this;
        }

        public Builder appConfigLocation(String appConfigLocation) {
            this.appConfigLocation = appConfigLocation;
            return this;
        }

        public Builder restartStrategy(RestartStrategy restartStrategy) {
            this.restartStrategy = restartStrategy;
            return this;
        }

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder restartScriptPath(String restartScriptPath) {
            this.restartScriptPath = restartScriptPath;
            return this;
        }

        public Builder targetPort(int targetPort) {
            this.targetPort = targetPort;
            return this;
        }

        public Builder fileMode(String fileMode) {
            this.fileMode = fileMode;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder group(String group) {
            this.group = group;
            return this;
        }

        public Builder backupKeystoreLocation(String backupKeystoreLocation) {
            this.backupKeystoreLocation = backupKeystoreLocation;
            return this;
        }

        public Builder healthCheckEndpoint(String healthCheckEndpoint) {
            this.healthCheckEndpoint = healthCheckEndpoint;
            return this;
        }

        public JavaDeploymentProfile build() {
            return new JavaDeploymentProfile(this);
        }
    }
}
