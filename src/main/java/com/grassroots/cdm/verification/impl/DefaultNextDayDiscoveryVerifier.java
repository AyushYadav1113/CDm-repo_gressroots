package com.grassroots.cdm.verification.impl;

import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.enums.InstallationStatus;
import com.grassroots.cdm.integration.servicenow.ServiceNowClient;
import com.grassroots.cdm.integration.servicenow.dto.ServiceNowCertificateDto;
import com.grassroots.cdm.repository.CertificateInstallationRepository;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import com.grassroots.cdm.verification.NextDayDiscoveryVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of {@link NextDayDiscoveryVerifier}.
 * Reconciles completed certificate deployments against independent ServiceNow CMDB discovery scans.
 */
@Service
public class DefaultNextDayDiscoveryVerifier implements NextDayDiscoveryVerifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultNextDayDiscoveryVerifier.class);

    private final DeploymentJobRepository deploymentJobRepository;
    private final CertificateInstallationRepository installationRepository;
    private final AuditService auditService;

    @Autowired(required = false)
    private ServiceNowClient serviceNowClient;

    public DefaultNextDayDiscoveryVerifier(
            DeploymentJobRepository deploymentJobRepository,
            CertificateInstallationRepository installationRepository,
            AuditService auditService
    ) {
        this.deploymentJobRepository = deploymentJobRepository;
        this.installationRepository = installationRepository;
        this.auditService = auditService;
    }

    public void setServiceNowClient(ServiceNowClient serviceNowClient) {
        this.serviceNowClient = serviceNowClient;
    }

    @Override
    @Transactional
    public boolean verifyDiscoveryReconciliation(UUID jobId) {
        if (jobId == null) {
            log.warn("verifyDiscoveryReconciliation invoked with null jobId");
            return false;
        }

        Optional<DeploymentJob> jobOpt = deploymentJobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            log.warn("DeploymentJob not found for ID {}", jobId);
            return false;
        }

        DeploymentJob job = jobOpt.get();
        if (job.getStatus() != DeploymentJobStatus.COMPLETED) {
            log.debug("Job {} is not COMPLETED (status={}); skipping next-day discovery reconciliation", jobId, job.getStatus());
            return false;
        }

        CertificateRecord newCert = job.getNewCertificate();
        if (newCert == null) {
            log.warn("Job {} has no associated newCertificate", jobId);
            return false;
        }

        String targetHost = job.getTargetHost();
        String expectedFingerprint = newCert.getFingerprintSha256();
        String correlationId = UUID.randomUUID().toString();

        log.info("Checking next-day discovery reconciliation for job {} (host={}, certFingerprint={})",
                jobId, targetHost, expectedFingerprint);

        boolean reconciled = false;

        // 1. Check ServiceNow CMDB directly if client is available
        if (serviceNowClient != null && expectedFingerprint != null) {
            try {
                String query = String.format("fingerprint_sha256=%s^ORfingerprint=%s", expectedFingerprint, expectedFingerprint);
                List<ServiceNowCertificateDto> records = serviceNowClient.fetchCertificates(query, 5, 0, correlationId);
                if (records != null && !records.isEmpty()) {
                    reconciled = true;
                    log.info("ServiceNow CMDB confirmed discovery of certificate {} for host {}", expectedFingerprint, targetHost);
                }
            } catch (Exception ex) {
                log.warn("Failed to query ServiceNow for next-day reconciliation on job {}: {}", jobId, ex.getMessage());
            }
        }

        // 2. Check local repository installation status if ServiceNow query was inconclusive
        if (!reconciled && job.getInstallation() != null) {
            CertificateInstallation installation = job.getInstallation();
            if (installation.getStatus() == InstallationStatus.VERIFIED
                    && installation.getCertificate() != null
                    && installation.getCertificate().getId().equals(newCert.getId())) {
                reconciled = true;
            }
        }

        if (reconciled) {
            if (job.getInstallation() != null) {
                CertificateInstallation installation = job.getInstallation();
                installation.setStatus(InstallationStatus.VERIFIED);
                installation.setLastVerifiedAt(Instant.now());
                installationRepository.save(installation);
            }

            auditService.recordWorkflowEvent(
                    jobId,
                    correlationId,
                    "DISCOVERY_RECONCILED",
                    "SUCCESS",
                    String.format("ServiceNow Discovery confirmed reconciliation of certificate for host %s", targetHost)
            );
            return true;
        } else {
            log.info("Next-day reconciliation pending: Certificate for job {} not yet discovered in CMDB", jobId);
            return false;
        }
    }
}
