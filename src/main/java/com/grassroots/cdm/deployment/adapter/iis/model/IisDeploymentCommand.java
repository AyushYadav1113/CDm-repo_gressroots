package com.grassroots.cdm.deployment.adapter.iis.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Structured deployment command model dispatched to the MID Server for IIS/Windows execution.
 * Ensures the MID Server has explicit, safe execution parameters rather than arbitrary shell strings.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IisDeploymentCommand {

    public enum Operation {
        DEPLOY,
        ROLLBACK,
        VERIFY,
        QUERY_CURRENT_BINDING
    }

    private UUID jobId;
    private String idempotencyKey;
    private Operation operation = Operation.DEPLOY;
    private String siteName = "Default Web Site";
    private int port = 443;
    private String ipAddress = "*";
    private String hostHeader;
    private boolean requireSni = true;
    private String storeLocation = "LocalMachine";
    private String storeName = "My";
    private String targetThumbprint;
    private String previousThumbprint;
    private String vaultSecretReference;
    private List<String> privateKeyAclAccounts = List.of("IIS_IUSRS", "NT SERVICE\\W3SVC");
    private boolean verifySslPort = true;
    private int executionTimeoutSeconds = 180;

    public IisDeploymentCommand() {
    }

    public IisDeploymentCommand(UUID jobId, String idempotencyKey, Operation operation,
                                String siteName, int port, String hostHeader,
                                String targetThumbprint, String previousThumbprint,
                                String vaultSecretReference) {
        this.jobId = jobId;
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        this.siteName = siteName != null ? siteName : "Default Web Site";
        this.port = port > 0 ? port : 443;
        this.hostHeader = hostHeader;
        this.targetThumbprint = targetThumbprint;
        this.previousThumbprint = previousThumbprint;
        this.vaultSecretReference = vaultSecretReference;
    }

    public Map<String, String> toParameterMap() {
        Map<String, String> params = new HashMap<>();
        params.put("iis.operation", operation.name());
        params.put("iis.siteName", siteName);
        params.put("iis.port", String.valueOf(port));
        params.put("iis.ipAddress", ipAddress);
        if (hostHeader != null && !hostHeader.isBlank()) {
            params.put("iis.hostHeader", hostHeader);
        }
        params.put("iis.requireSni", String.valueOf(requireSni));
        params.put("iis.storeLocation", storeLocation);
        params.put("iis.storeName", storeName);
        if (targetThumbprint != null) {
            params.put("iis.targetThumbprint", targetThumbprint);
        }
        if (previousThumbprint != null) {
            params.put("iis.previousThumbprint", previousThumbprint);
        }
        if (vaultSecretReference != null) {
            params.put("iis.vaultSecretReference", vaultSecretReference);
        }
        params.put("iis.privateKeyAclAccounts", String.join(",", privateKeyAclAccounts));
        params.put("iis.verifySslPort", String.valueOf(verifySslPort));
        params.put("iis.executionTimeoutSeconds", String.valueOf(executionTimeoutSeconds));
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

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getHostHeader() {
        return hostHeader;
    }

    public void setHostHeader(String hostHeader) {
        this.hostHeader = hostHeader;
    }

    public boolean isRequireSni() {
        return requireSni;
    }

    public void setRequireSni(boolean requireSni) {
        this.requireSni = requireSni;
    }

    public String getStoreLocation() {
        return storeLocation;
    }

    public void setStoreLocation(String storeLocation) {
        this.storeLocation = storeLocation;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
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

    public String getVaultSecretReference() {
        return vaultSecretReference;
    }

    public void setVaultSecretReference(String vaultSecretReference) {
        this.vaultSecretReference = vaultSecretReference;
    }

    public List<String> getPrivateKeyAclAccounts() {
        return privateKeyAclAccounts;
    }

    public void setPrivateKeyAclAccounts(List<String> privateKeyAclAccounts) {
        this.privateKeyAclAccounts = privateKeyAclAccounts != null ? new ArrayList<>(privateKeyAclAccounts) : Collections.emptyList();
    }

    public boolean isVerifySslPort() {
        return verifySslPort;
    }

    public void setVerifySslPort(boolean verifySslPort) {
        this.verifySslPort = verifySslPort;
    }

    public int getExecutionTimeoutSeconds() {
        return executionTimeoutSeconds;
    }

    public void setExecutionTimeoutSeconds(int executionTimeoutSeconds) {
        this.executionTimeoutSeconds = executionTimeoutSeconds;
    }

    @Override
    public String toString() {
        return "IisDeploymentCommand{" +
                "jobId=" + jobId +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", operation=" + operation +
                ", siteName='" + siteName + '\'' +
                ", port=" + port +
                ", hostHeader='" + hostHeader + '\'' +
                ", targetThumbprint='" + targetThumbprint + '\'' +
                ", previousThumbprint='" + previousThumbprint + '\'' +
                ", vaultSecretReference='[REDACTED]'" +
                '}';
    }
}
