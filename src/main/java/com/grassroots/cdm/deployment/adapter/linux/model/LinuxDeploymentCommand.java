package com.grassroots.cdm.deployment.adapter.linux.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grassroots.cdm.entity.enums.ServerTechnology;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Structured deployment command model dispatched to MID Server for Linux (Apache / Nginx) execution.
 * Completely eliminates arbitrary shell injection by strictly parameterizing paths, permissions, and service actions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LinuxDeploymentCommand {

    public enum Operation {
        DEPLOY,
        ROLLBACK,
        VALIDATE_CONFIG,
        VERIFY
    }

    private UUID jobId;
    private String idempotencyKey;
    private Operation operation = Operation.DEPLOY;
    private ServerTechnology technology;
    private String configFilePath;
    private String certificatePath;
    private String privateKeyPath;
    private LinuxFilePermissions certificatePermissions = LinuxFilePermissions.defaultCertificatePermissions();
    private LinuxFilePermissions privateKeyPermissions = LinuxFilePermissions.defaultPrivateKeyPermissions("root");
    private String targetThumbprint;
    private String previousThumbprint;
    private String previousConfigBackupPath;
    private String vaultSecretReference;
    private String configValidationCommand;
    private String serviceReloadCommand;
    private int targetPort = 443;
    private String hostHeader;
    private int executionTimeoutSeconds = 180;

    public LinuxDeploymentCommand() {
    }

    public LinuxDeploymentCommand(UUID jobId, String idempotencyKey, Operation operation,
                                  ServerTechnology technology, String configFilePath,
                                  String certificatePath, String privateKeyPath,
                                  String targetThumbprint, String previousThumbprint,
                                  String vaultSecretReference, String configValidationCommand,
                                  String serviceReloadCommand, int targetPort, String hostHeader) {
        this.jobId = jobId;
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        this.technology = technology;
        this.configFilePath = configFilePath;
        this.certificatePath = certificatePath;
        this.privateKeyPath = privateKeyPath;
        this.targetThumbprint = targetThumbprint;
        this.previousThumbprint = previousThumbprint;
        this.vaultSecretReference = vaultSecretReference;
        this.configValidationCommand = configValidationCommand;
        this.serviceReloadCommand = serviceReloadCommand;
        this.targetPort = targetPort > 0 ? targetPort : 443;
        this.hostHeader = hostHeader;
        if (configFilePath != null) {
            this.previousConfigBackupPath = configFilePath + ".cdm-bak";
        }
    }

    public Map<String, String> toParameterMap() {
        Map<String, String> params = new HashMap<>();
        params.put("linux.operation", operation.name());
        params.put("linux.technology", technology != null ? technology.name() : "UNKNOWN");
        params.put("linux.configFilePath", configFilePath);
        params.put("linux.certificatePath", certificatePath);
        params.put("linux.privateKeyPath", privateKeyPath);
        params.put("linux.certFileMode", certificatePermissions.fileMode());
        params.put("linux.certOwner", certificatePermissions.owner());
        params.put("linux.certGroup", certificatePermissions.group());
        params.put("linux.keyFileMode", privateKeyPermissions.fileMode());
        params.put("linux.keyOwner", privateKeyPermissions.owner());
        params.put("linux.keyGroup", privateKeyPermissions.group());
        if (targetThumbprint != null) {
            params.put("linux.targetThumbprint", targetThumbprint);
        }
        if (previousThumbprint != null) {
            params.put("linux.previousThumbprint", previousThumbprint);
        }
        if (previousConfigBackupPath != null) {
            params.put("linux.previousConfigBackupPath", previousConfigBackupPath);
        }
        if (vaultSecretReference != null) {
            params.put("linux.vaultSecretReference", vaultSecretReference);
        }
        params.put("linux.configValidationCommand", configValidationCommand);
        params.put("linux.serviceReloadCommand", serviceReloadCommand);
        params.put("linux.targetPort", String.valueOf(targetPort));
        if (hostHeader != null) {
            params.put("linux.hostHeader", hostHeader);
        }
        params.put("linux.executionTimeoutSeconds", String.valueOf(executionTimeoutSeconds));
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

    public ServerTechnology getTechnology() {
        return technology;
    }

    public void setTechnology(ServerTechnology technology) {
        this.technology = technology;
    }

    public String getConfigFilePath() {
        return configFilePath;
    }

    public void setConfigFilePath(String configFilePath) {
        this.configFilePath = configFilePath;
        if (configFilePath != null) {
            this.previousConfigBackupPath = configFilePath + ".cdm-bak";
        }
    }

    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public LinuxFilePermissions getCertificatePermissions() {
        return certificatePermissions;
    }

    public void setCertificatePermissions(LinuxFilePermissions certificatePermissions) {
        this.certificatePermissions = certificatePermissions;
    }

    public LinuxFilePermissions getPrivateKeyPermissions() {
        return privateKeyPermissions;
    }

    public void setPrivateKeyPermissions(LinuxFilePermissions privateKeyPermissions) {
        this.privateKeyPermissions = privateKeyPermissions;
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

    public String getPreviousConfigBackupPath() {
        return previousConfigBackupPath;
    }

    public void setPreviousConfigBackupPath(String previousConfigBackupPath) {
        this.previousConfigBackupPath = previousConfigBackupPath;
    }

    public String getVaultSecretReference() {
        return vaultSecretReference;
    }

    public void setVaultSecretReference(String vaultSecretReference) {
        this.vaultSecretReference = vaultSecretReference;
    }

    public String getConfigValidationCommand() {
        return configValidationCommand;
    }

    public void setConfigValidationCommand(String configValidationCommand) {
        this.configValidationCommand = configValidationCommand;
    }

    public String getServiceReloadCommand() {
        return serviceReloadCommand;
    }

    public void setServiceReloadCommand(String serviceReloadCommand) {
        this.serviceReloadCommand = serviceReloadCommand;
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

    public int getExecutionTimeoutSeconds() {
        return executionTimeoutSeconds;
    }

    public void setExecutionTimeoutSeconds(int executionTimeoutSeconds) {
        this.executionTimeoutSeconds = executionTimeoutSeconds;
    }

    @Override
    public String toString() {
        return "LinuxDeploymentCommand{" +
                "jobId=" + jobId +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", operation=" + operation +
                ", technology=" + technology +
                ", configFilePath='" + configFilePath + '\'' +
                ", certificatePath='" + certificatePath + '\'' +
                ", targetThumbprint='" + targetThumbprint + '\'' +
                ", previousThumbprint='" + previousThumbprint + '\'' +
                ", vaultSecretReference='[REDACTED]'" +
                ", certPermissions=" + certificatePermissions.fileMode() +
                ", keyPermissions=" + privateKeyPermissions.fileMode() +
                '}';
    }
}
