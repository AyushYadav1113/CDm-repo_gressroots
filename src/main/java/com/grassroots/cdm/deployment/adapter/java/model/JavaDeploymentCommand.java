package com.grassroots.cdm.deployment.adapter.java.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grassroots.cdm.deployment.adapter.java.profile.KeystoreType;
import com.grassroots.cdm.deployment.adapter.java.profile.RestartStrategy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Structured deployment command dispatched to MID Server for Java keystore execution.
 * Completely eliminates arbitrary shell injection by strictly parameterizing paths,
 * aliases, keystore formats, and service restart procedures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JavaDeploymentCommand {

    public enum Operation {
        DEPLOY,
        ROLLBACK,
        VALIDATE_KEYSTORE,
        VERIFY
    }

    private UUID jobId;
    private String idempotencyKey;
    private Operation operation = Operation.DEPLOY;
    private KeystoreType keystoreType = KeystoreType.PKCS12;
    private String keystoreLocation;
    private String backupKeystoreLocation;
    private String keyAlias;
    private String targetThumbprint;
    private String previousThumbprint;
    private String keystorePasswordVaultRef;
    private String keyPasswordVaultRef;
    private String appConfigLocation;
    private String backupConfigLocation;
    private RestartStrategy restartStrategy = RestartStrategy.SYSTEMD_SERVICE;
    private String serviceName;
    private String restartCommand;
    private String fileMode = "0640";
    private String owner = "root";
    private String group = "root";
    private int targetPort = 8443;
    private String hostHeader;
    private String healthCheckEndpoint = "/actuator/health";
    private int executionTimeoutSeconds = 180;

    public JavaDeploymentCommand() {
    }

    public JavaDeploymentCommand(UUID jobId, String idempotencyKey, Operation operation,
                                 KeystoreType keystoreType, String keystoreLocation,
                                 String keyAlias, String targetThumbprint, String previousThumbprint,
                                 String keystorePasswordVaultRef, RestartStrategy restartStrategy,
                                 String serviceName, int targetPort, String hostHeader) {
        this.jobId = jobId;
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        this.keystoreType = keystoreType != null ? keystoreType : KeystoreType.PKCS12;
        this.keystoreLocation = keystoreLocation;
        this.backupKeystoreLocation = keystoreLocation != null ? keystoreLocation + ".cdm-bak" : null;
        this.keyAlias = keyAlias;
        this.targetThumbprint = targetThumbprint;
        this.previousThumbprint = previousThumbprint;
        this.keystorePasswordVaultRef = keystorePasswordVaultRef;
        this.restartStrategy = restartStrategy != null ? restartStrategy : RestartStrategy.SYSTEMD_SERVICE;
        this.serviceName = serviceName;
        this.targetPort = targetPort > 0 ? targetPort : 8443;
        this.hostHeader = hostHeader;
    }

    public Map<String, String> toParameterMap() {
        Map<String, String> params = new HashMap<>();
        params.put("java.operation", operation.name());
        params.put("java.keystoreType", keystoreType.getFormat());
        params.put("java.keystoreLocation", keystoreLocation);
        if (backupKeystoreLocation != null) {
            params.put("java.backupKeystoreLocation", backupKeystoreLocation);
        }
        params.put("java.keyAlias", keyAlias);
        if (targetThumbprint != null) {
            params.put("java.targetThumbprint", targetThumbprint);
        }
        if (previousThumbprint != null) {
            params.put("java.previousThumbprint", previousThumbprint);
        }
        if (keystorePasswordVaultRef != null) {
            params.put("java.keystorePasswordVaultRef", keystorePasswordVaultRef);
        }
        if (keyPasswordVaultRef != null) {
            params.put("java.keyPasswordVaultRef", keyPasswordVaultRef);
        }
        if (appConfigLocation != null) {
            params.put("java.appConfigLocation", appConfigLocation);
        }
        if (backupConfigLocation != null) {
            params.put("java.backupConfigLocation", backupConfigLocation);
        }
        params.put("java.restartStrategy", restartStrategy.name());
        if (serviceName != null) {
            params.put("java.serviceName", serviceName);
        }
        if (restartCommand != null) {
            params.put("java.restartCommand", restartCommand);
        }
        params.put("java.fileMode", fileMode);
        params.put("java.owner", owner);
        params.put("java.group", group);
        params.put("java.targetPort", String.valueOf(targetPort));
        if (hostHeader != null) {
            params.put("java.hostHeader", hostHeader);
        }
        if (healthCheckEndpoint != null) {
            params.put("java.healthCheckEndpoint", healthCheckEndpoint);
        }
        params.put("java.executionTimeoutSeconds", String.valueOf(executionTimeoutSeconds));
        return params;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    public KeystoreType getKeystoreType() {
        return keystoreType;
    }

    public void setKeystoreType(KeystoreType keystoreType) {
        this.keystoreType = keystoreType;
    }

    public String getKeystoreLocation() {
        return keystoreLocation;
    }

    public void setKeystoreLocation(String keystoreLocation) {
        this.keystoreLocation = keystoreLocation;
        if (keystoreLocation != null && this.backupKeystoreLocation == null) {
            this.backupKeystoreLocation = keystoreLocation + ".cdm-bak";
        }
    }

    public String getBackupKeystoreLocation() {
        return backupKeystoreLocation;
    }

    public void setBackupKeystoreLocation(String backupKeystoreLocation) {
        this.backupKeystoreLocation = backupKeystoreLocation;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public void setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }

    public String getTargetThumbprint() {
        return targetThumbprint;
    }

    public void setTargetThumbprint(String targetThumbprint) {
        this.targetThumbprint = targetThumbprint;
    }

    public String getPreviousThumbprint() {
        return previousThumbprint;
    }

    public void setPreviousThumbprint(String previousThumbprint) {
        this.previousThumbprint = previousThumbprint;
    }

    public String getKeystorePasswordVaultRef() {
        return keystorePasswordVaultRef;
    }

    public void setKeystorePasswordVaultRef(String keystorePasswordVaultRef) {
        this.keystorePasswordVaultRef = keystorePasswordVaultRef;
    }

    public String getKeyPasswordVaultRef() {
        return keyPasswordVaultRef;
    }

    public void setKeyPasswordVaultRef(String keyPasswordVaultRef) {
        this.keyPasswordVaultRef = keyPasswordVaultRef;
    }

    public String getAppConfigLocation() {
        return appConfigLocation;
    }

    public void setAppConfigLocation(String appConfigLocation) {
        this.appConfigLocation = appConfigLocation;
        if (appConfigLocation != null && this.backupConfigLocation == null) {
            this.backupConfigLocation = appConfigLocation + ".cdm-bak";
        }
    }

    public String getBackupConfigLocation() {
        return backupConfigLocation;
    }

    public void setBackupConfigLocation(String backupConfigLocation) {
        this.backupConfigLocation = backupConfigLocation;
    }

    public RestartStrategy getRestartStrategy() {
        return restartStrategy;
    }

    public void setRestartStrategy(RestartStrategy restartStrategy) {
        this.restartStrategy = restartStrategy;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getRestartCommand() {
        return restartCommand;
    }

    public void setRestartCommand(String restartCommand) {
        this.restartCommand = restartCommand;
    }

    public String getFileMode() {
        return fileMode;
    }

    public void setFileMode(String fileMode) {
        this.fileMode = fileMode;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(int targetPort) {
        this.targetPort = targetPort;
    }

    public String getHostHeader() {
        return hostHeader;
    }

    public void setHostHeader(String hostHeader) {
        this.hostHeader = hostHeader;
    }

    public String getHealthCheckEndpoint() {
        return healthCheckEndpoint;
    }

    public void setHealthCheckEndpoint(String healthCheckEndpoint) {
        this.healthCheckEndpoint = healthCheckEndpoint;
    }

    public int getExecutionTimeoutSeconds() {
        return executionTimeoutSeconds;
    }

    public void setExecutionTimeoutSeconds(int executionTimeoutSeconds) {
        this.executionTimeoutSeconds = executionTimeoutSeconds;
    }

    @Override
    public String toString() {
        return "JavaDeploymentCommand{" +
                "jobId=" + jobId +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", operation=" + operation +
                ", keystoreType=" + keystoreType +
                ", keystoreLocation='" + keystoreLocation + '\'' +
                ", keyAlias='" + keyAlias + '\'' +
                ", targetThumbprint='" + targetThumbprint + '\'' +
                ", restartStrategy=" + restartStrategy +
                ", serviceName='" + serviceName + '\'' +
                ", targetPort=" + targetPort +
                ", fileMode='" + fileMode + '\'' +
                ", keystorePasswordVaultRef='[REDACTED]'" +
                ", keyPasswordVaultRef='[REDACTED]'" +
                '}';
    }
}
