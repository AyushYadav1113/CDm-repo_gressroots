package com.grassroots.cdm.deployment.dispatcher.impl;

import com.grassroots.cdm.deployment.DeploymentDispatcher;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.integration.midserver.MidServerClient;
import com.grassroots.cdm.integration.midserver.config.MidServerProperties;
import com.grassroots.cdm.integration.midserver.dto.CertificateReferenceDto;
import com.grassroots.cdm.integration.midserver.dto.ExecutionParametersDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobRequestDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobResponseDto;
import com.grassroots.cdm.integration.midserver.dto.TargetServerInfoDto;
import com.grassroots.cdm.integration.midserver.service.MidServerExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Production implementation of {@link DeploymentDispatcher}.
 * Bridges CDM's orchestration decision layer to the ServiceNow MID Server execution layer.
 */
@Component
public class DefaultDeploymentDispatcher implements DeploymentDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultDeploymentDispatcher.class);

    private final MidServerClient midServerClient;
    private final MidServerExecutionService executionService;
    private final MidServerProperties properties;

    public DefaultDeploymentDispatcher(
            MidServerClient midServerClient,
            MidServerExecutionService executionService,
            MidServerProperties properties) {
        this.midServerClient = midServerClient;
        this.executionService = executionService;
        this.properties = properties;
    }

    @Override
    public String dispatchJob(DeploymentJob job) {
        if (job == null) {
            throw new IllegalArgumentException("DeploymentJob cannot be null");
        }

        TargetServer targetServer = job.getTargetServer();
        MidServer midServer = targetServer != null ? targetServer.getMidServer() : null;

        if (midServer != null && midServer.getStatus() != MidServerStatus.UP) {
            log.warn("Target MID Server {} is not UP (status: {})", midServer.getName(), midServer.getStatus());
        }

        String endpoint = (midServer != null && midServer.getEndpoint() != null)
                ? midServer.getEndpoint()
                : properties.getBaseUrl();

        MidServerJobRequestDto request = buildJobRequest(job, targetServer);

        log.info("Dispatching deployment job {} to MID Server at {}", job.getId(), endpoint);
        MidServerJobResponseDto response = midServerClient.submitJob(endpoint, request);

        // Persist execution tracking
        executionService.recordDispatch(job, midServer, response.getTaskId(), job.getIdempotencyKey());

        return response.getTaskId();
    }

    private MidServerJobRequestDto buildJobRequest(DeploymentJob job, TargetServer targetServer) {
        TargetServerInfoDto serverDto;
        if (targetServer != null) {
            serverDto = new TargetServerInfoDto(
                    targetServer.getHostname(),
                    targetServer.getIpAddress(),
                    targetServer.getOperatingSystem(),
                    targetServer.getTechnology(),
                    job.getTargetPort(),
                    targetServer.getEnvironment()
            );
        } else {
            serverDto = new TargetServerInfoDto(
                    job.getTargetHost(),
                    null,
                    null,
                    null,
                    job.getTargetPort(),
                    null
            );
        }

        CertificateRecord newCert = job.getNewCertificate();
        CertificateReferenceDto certRef = null;
        if (newCert != null) {
            List<String> sanList = Collections.emptyList();
            if (newCert.getSubjectAlternativeNames() != null && !newCert.getSubjectAlternativeNames().isBlank()) {
                sanList = Arrays.stream(newCert.getSubjectAlternativeNames().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }

            // Secure reference only - never transmit private keys!
            String secretLocator = "cyberark://Grassroots-CertVault/Certs/" + newCert.getThumbprint();
            certRef = new CertificateReferenceDto(
                    newCert.getId(),
                    newCert.getSerialNumber(),
                    newCert.getThumbprint(),
                    newCert.getCommonName(),
                    sanList,
                    secretLocator,
                    newCert.getValidTo()
            );
        }

        CertificateInstallation installation = job.getInstallation();
        String installPath = (installation != null && installation.getInstallationPath() != null)
                ? installation.getInstallationPath()
                : "/etc/ssl/certs";
        String binding = (installation != null && installation.getBindingInfo() != null)
                ? installation.getBindingInfo()
                : "default";

        Map<String, String> customSettings = new HashMap<>();
        if (installation != null && installation.getTechnology() != null) {
            customSettings.put("technology", installation.getTechnology().name());
        }
        if (job.getTargetType() != null) {
            customSettings.put("targetType", job.getTargetType());
        }

        ExecutionParametersDto execParams = new ExecutionParametersDto(
                installPath,
                binding,
                true,
                false,
                true,
                properties.getExecutionTimeoutSeconds(),
                customSettings
        );

        return new MidServerJobRequestDto(
                job.getId(),
                job.getIdempotencyKey(),
                serverDto,
                job.getDeploymentType(),
                certRef,
                execParams
        );
    }
}
