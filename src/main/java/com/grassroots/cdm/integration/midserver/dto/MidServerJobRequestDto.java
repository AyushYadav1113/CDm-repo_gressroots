package com.grassroots.cdm.integration.midserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grassroots.cdm.entity.enums.DeploymentType;

import java.util.UUID;

/**
 * The MID Server Job API Contract dispatched by CDM to the ServiceNow MID Server.
 * Encapsulates the execution parameters, target host metadata, and certificate identity.
 * Credentials and private keys are never included directly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MidServerJobRequestDto {

    private UUID jobId;
    private String idempotencyKey;
    private TargetServerInfoDto targetServer;
    private DeploymentType deploymentType;
    private CertificateReferenceDto certificateReference;
    private ExecutionParametersDto executionParameters;

    public MidServerJobRequestDto() {
    }

    public MidServerJobRequestDto(UUID jobId, String idempotencyKey, TargetServerInfoDto targetServer,
                                  DeploymentType deploymentType, CertificateReferenceDto certificateReference,
                                  ExecutionParametersDto executionParameters) {
        this.jobId = jobId;
        this.idempotencyKey = idempotencyKey;
        this.targetServer = targetServer;
        this.deploymentType = deploymentType;
        this.certificateReference = certificateReference;
        this.executionParameters = executionParameters;
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

    public TargetServerInfoDto getTargetServer() {
        return targetServer;
    }

    public void setTargetServer(TargetServerInfoDto targetServer) {
        this.targetServer = targetServer;
    }

    public DeploymentType getDeploymentType() {
        return deploymentType;
    }

    public void setDeploymentType(DeploymentType deploymentType) {
        this.deploymentType = deploymentType;
    }

    public CertificateReferenceDto getCertificateReference() {
        return certificateReference;
    }

    public void setCertificateReference(CertificateReferenceDto certificateReference) {
        this.certificateReference = certificateReference;
    }

    public ExecutionParametersDto getExecutionParameters() {
        return executionParameters;
    }

    public void setExecutionParameters(ExecutionParametersDto executionParameters) {
        this.executionParameters = executionParameters;
    }

    @Override
    public String toString() {
        return "MidServerJobRequestDto{" +
                "jobId=" + jobId +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", targetHost=" + (targetServer != null ? targetServer.getHostname() : "null") +
                ", deploymentType=" + deploymentType +
                ", certificateCN=" + (certificateReference != null ? certificateReference.getCommonName() : "null") +
                ", certificateThumbprint=" + (certificateReference != null ? certificateReference.getThumbprint() : "null") +
                '}';
    }
}
