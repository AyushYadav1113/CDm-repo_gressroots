package com.grassroots.cdm.deployment.planner.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.deployment.planner.DeploymentPlanner;
import com.grassroots.cdm.deployment.planner.config.DeploymentPlannerProperties;
import com.grassroots.cdm.deployment.planner.exception.AmbiguousMatchException;
import com.grassroots.cdm.deployment.planner.exception.DeploymentAlreadyCompletedException;
import com.grassroots.cdm.deployment.planner.exception.DeploymentPlanningException;
import com.grassroots.cdm.deployment.planner.exception.DuplicateDeploymentJobException;
import com.grassroots.cdm.deployment.planner.exception.NoMatchException;
import com.grassroots.cdm.deployment.planner.generator.IdempotencyKeyGenerator;
import com.grassroots.cdm.deployment.planner.generator.JobReferenceGenerator;
import com.grassroots.cdm.deployment.planner.model.DeploymentPlanRejection;
import com.grassroots.cdm.deployment.planner.model.DeploymentPlanResult;
import com.grassroots.cdm.deployment.planner.rules.DeploymentTypeResolver;
import com.grassroots.cdm.deployment.planner.rules.MidServerValidator;
import com.grassroots.cdm.deployment.planner.rules.PriorityCalculator;
import com.grassroots.cdm.deployment.planner.rules.TargetServerValidator;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.CertificateReplacement;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.JobPriority;
import com.grassroots.cdm.entity.enums.MatchStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.repository.CertificateInstallationRepository;
import com.grassroots.cdm.repository.CertificateReplacementRepository;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of DeploymentPlanner.
 * Enforces deterministic planning rules, idempotency key generation, MID Server validation,
 * technology resolution, priority calculation, and duplicate prevention.
 */
@Service
@Transactional
public class DefaultDeploymentPlanner implements DeploymentPlanner {

    private static final Logger log = LoggerFactory.getLogger(DefaultDeploymentPlanner.class);

    private final CertificateReplacementRepository replacementRepository;
    private final CertificateInstallationRepository installationRepository;
    private final DeploymentJobRepository deploymentJobRepository;
    private final AuditService auditService;
    private final DeploymentTypeResolver deploymentTypeResolver;
    private final PriorityCalculator priorityCalculator;
    private final TargetServerValidator targetServerValidator;
    private final MidServerValidator midServerValidator;
    private final DeploymentPlannerProperties properties;
    private final ObjectMapper objectMapper;

    public DefaultDeploymentPlanner(
            CertificateReplacementRepository replacementRepository,
            CertificateInstallationRepository installationRepository,
            DeploymentJobRepository deploymentJobRepository,
            AuditService auditService,
            DeploymentTypeResolver deploymentTypeResolver,
            PriorityCalculator priorityCalculator,
            TargetServerValidator targetServerValidator,
            MidServerValidator midServerValidator,
            DeploymentPlannerProperties properties,
            ObjectMapper objectMapper
    ) {
        this.replacementRepository = replacementRepository;
        this.installationRepository = installationRepository;
        this.deploymentJobRepository = deploymentJobRepository;
        this.auditService = auditService;
        this.deploymentTypeResolver = deploymentTypeResolver;
        this.priorityCalculator = priorityCalculator;
        this.targetServerValidator = targetServerValidator;
        this.midServerValidator = midServerValidator;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public DeploymentPlanResult planDeployments(UUID replacementId) {
        if (replacementId == null) {
            throw new IllegalArgumentException("Replacement ID must not be null");
        }
        CertificateReplacement replacement = replacementRepository.findById(replacementId)
                .orElseThrow(() -> new NoMatchException("Certificate replacement record not found for ID: " + replacementId));
        return planDeployments(replacement);
    }

    @Override
    public DeploymentPlanResult planDeployments(CertificateReplacement replacement) {
        validateReplacementEligibility(replacement);

        CertificateRecord oldCert = replacement.getOldCertificate();
        CertificateRecord newCert = replacement.getNewCertificate();

        List<CertificateInstallation> installations = installationRepository.findByCertificateId(oldCert.getId());
        List<DeploymentJob> plannedJobs = new ArrayList<>();
        List<DeploymentPlanRejection> rejections = new ArrayList<>();

        if (installations.isEmpty()) {
            log.info("No active installations found for expiring certificate id={}, CN='{}'",
                    oldCert.getId(), oldCert.getCommonName());
            return new DeploymentPlanResult(
                    replacement.getId(),
                    oldCert.getId(),
                    newCert.getId(),
                    0,
                    plannedJobs,
                    rejections,
                    Instant.now()
            );
        }

        for (CertificateInstallation installation : installations) {
            String serverHost = (installation.getServer() != null) ? installation.getServer().getHostname() : "unknown";
            try {
                DeploymentJob job = planDeploymentForInstallation(replacement, installation);
                plannedJobs.add(job);
            } catch (DeploymentPlanningException ex) {
                log.warn("Deployment planning rejected for installation id={}, server={}: {}",
                        installation.getId(), serverHost, ex.getMessage());
                rejections.add(new DeploymentPlanRejection(
                        installation.getId(),
                        serverHost,
                        ex.getMessage(),
                        ex.getClass().getSimpleName()
                ));
            }
        }

        return new DeploymentPlanResult(
                replacement.getId(),
                oldCert.getId(),
                newCert.getId(),
                installations.size(),
                plannedJobs,
                rejections,
                Instant.now()
        );
    }

    @Override
    public DeploymentJob planDeploymentForInstallation(CertificateReplacement replacement, CertificateInstallation installation) {
        validateReplacementEligibility(replacement);

        if (installation == null) {
            throw new IllegalArgumentException("CertificateInstallation must not be null");
        }

        CertificateRecord oldCert = replacement.getOldCertificate();
        CertificateRecord newCert = replacement.getNewCertificate();

        // 1. Validate target server exists and is not decommissioned
        TargetServer server = targetServerValidator.validate(installation);

        // 2. Validate suitable operational MID Server
        midServerValidator.validate(server);

        // 3. Resolve deployment type based on technology
        ServerTechnology tech = installation.getTechnology() != null ? installation.getTechnology() : server.getTechnology();
        DeploymentTypeResolver.ResolvedDeploymentType resolved = deploymentTypeResolver.resolve(tech);

        // 4. Generate deterministic idempotency key
        String idempotencyKey = IdempotencyKeyGenerator.generateKey(oldCert, newCert, installation);

        // 5. Prevent duplicate and already completed jobs
        checkExistingJobDuplicates(idempotencyKey, installation.getId(), newCert.getId());

        // 6. Calculate deterministic job priority
        JobPriority priority = priorityCalculator.calculatePriority(oldCert, server);

        // 7. Generate trackable job reference
        String jobReference = JobReferenceGenerator.generateReference(idempotencyKey);

        // 8. Generate creation reason
        String creationReason = buildCreationReason(replacement, installation, server, resolved);

        // 9. Instantiate DeploymentJob
        DeploymentJob job = new DeploymentJob();
        job.setJobReference(jobReference);
        job.setIdempotencyKey(idempotencyKey);
        job.setOldCertificate(oldCert);
        job.setNewCertificate(newCert);
        job.setTargetServer(server);
        job.setInstallation(installation);
        job.setTargetHost(server.getHostname());
        job.setTargetPort(installation.getPort());
        job.setTargetType(resolved.targetType());
        job.setDeploymentType(resolved.deploymentType());
        job.setStatus(DeploymentJobStatus.PENDING);
        job.setPriority(priority);
        job.setCreationReason(creationReason);
        job.setMaxRetries(properties.getDefaultMaxRetries());
        job.setScheduledAt(Instant.now());

        DeploymentJob savedJob = deploymentJobRepository.saveAndFlush(job);
        log.info("Planned DeploymentJob reference={}, id={}, targetServer={}, tech={}, deploymentType={}, priority={}",
                savedJob.getJobReference(), savedJob.getId(), server.getHostname(), tech, resolved.deploymentType(), priority);

        // 10. Record tamper-evident audit trail
        recordAuditTrail(savedJob, replacement, installation);

        return savedJob;
    }

    @Override
    public List<DeploymentPlanResult> planAllPendingDeployments() {
        List<CertificateReplacement> autoMatched = replacementRepository.findByMatchStatus(MatchStatus.AUTO_MATCHED);
        List<CertificateReplacement> manuallyConfirmed = replacementRepository.findByMatchStatus(MatchStatus.MANUALLY_CONFIRMED);

        List<CertificateReplacement> candidates = new ArrayList<>(autoMatched);
        candidates.addAll(manuallyConfirmed);

        List<DeploymentPlanResult> results = new ArrayList<>();
        for (CertificateReplacement replacement : candidates) {
            try {
                DeploymentPlanResult result = planDeployments(replacement);
                results.add(result);
            } catch (DeploymentPlanningException ex) {
                log.warn("Skipping pending replacement id={} during batch planning: {}", replacement.getId(), ex.getMessage());
            }
        }
        return results;
    }

    private void validateReplacementEligibility(CertificateReplacement replacement) {
        if (replacement == null) {
            throw new NoMatchException("Certificate replacement must not be null (NO_MATCH).");
        }
        if (replacement.getOldCertificate() == null || replacement.getNewCertificate() == null) {
            throw new NoMatchException("Certificate replacement is missing old or new certificate records.");
        }

        MatchStatus status = replacement.getMatchStatus();
        if (status == null || status == MatchStatus.REJECTED || status == MatchStatus.SUPERSEDED) {
            throw new NoMatchException("Certificate replacement is in " + status + " status (NO_MATCH) and cannot be deployed.");
        }

        if (status == MatchStatus.PENDING_REVIEW) {
            if (!properties.isAllowManualReviewOverride()) {
                throw new AmbiguousMatchException(
                        replacement.getId(),
                        "Certificate replacement " + replacement.getId() + " is in PENDING_REVIEW status (ambiguous match) and requires manual administrator confirmation before automated deployment."
                );
            }
        }
    }

    private void checkExistingJobDuplicates(String idempotencyKey, UUID installationId, UUID newCertificateId) {
        Optional<DeploymentJob> byIdempotency = deploymentJobRepository.findByIdempotencyKey(idempotencyKey);
        if (byIdempotency.isPresent()) {
            DeploymentJob existing = byIdempotency.get();
            if (existing.getStatus() == DeploymentJobStatus.COMPLETED) {
                throw new DeploymentAlreadyCompletedException(
                        existing.getId(),
                        idempotencyKey,
                        "Deployment job " + existing.getJobReference() + " for installation " + installationId + " has already been successfully COMPLETED."
                );
            }
            throw new DuplicateDeploymentJobException(
                    idempotencyKey,
                    existing.getId(),
                    "Deployment job " + existing.getJobReference() + " already exists with status " + existing.getStatus() + " for idempotency key: " + idempotencyKey
            );
        }

        Optional<DeploymentJob> byInstall = deploymentJobRepository.findByInstallationIdAndNewCertificateId(installationId, newCertificateId);
        if (byInstall.isPresent()) {
            DeploymentJob existing = byInstall.get();
            if (existing.getStatus() == DeploymentJobStatus.COMPLETED) {
                throw new DeploymentAlreadyCompletedException(
                        existing.getId(),
                        existing.getIdempotencyKey(),
                        "Deployment job " + existing.getJobReference() + " for installation " + installationId + " and new certificate " + newCertificateId + " is already COMPLETED."
                );
            }
            throw new DuplicateDeploymentJobException(
                    existing.getIdempotencyKey(),
                    existing.getId(),
                    "Deployment job " + existing.getJobReference() + " already exists for installation " + installationId + " with status: " + existing.getStatus()
            );
        }
    }

    private String buildCreationReason(
            CertificateReplacement replacement,
            CertificateInstallation installation,
            TargetServer server,
            DeploymentTypeResolver.ResolvedDeploymentType resolved
    ) {
        String binding = (installation.getBindingInfo() != null && !installation.getBindingInfo().isBlank())
                ? installation.getBindingInfo()
                : "default";

        return String.format(
                "Automated renewal deployment planned for installation [%s:%d, binding: %s] on server [%s, %s] replacing expiring certificate [%s, thumbprint: %s, validTo: %s] with renewed certificate [%s, thumbprint: %s, validTo: %s]. Match status: %s, score: %.2f.",
                server.getHostname(),
                installation.getPort(),
                binding,
                server.getHostname(),
                resolved.deploymentType(),
                replacement.getOldCertificate().getCommonName(),
                replacement.getOldCertificate().getThumbprint(),
                replacement.getOldCertificate().getValidTo(),
                replacement.getNewCertificate().getCommonName(),
                replacement.getNewCertificate().getThumbprint(),
                replacement.getNewCertificate().getValidTo(),
                replacement.getMatchStatus(),
                replacement.getMatchingScore()
        );
    }

    private void recordAuditTrail(DeploymentJob job, CertificateReplacement replacement, CertificateInstallation installation) {
        try {
            String detailsJson = objectMapper.writeValueAsString(Map.of(
                    "jobReference", job.getJobReference(),
                    "idempotencyKey", job.getIdempotencyKey(),
                    "targetServer", job.getTargetHost(),
                    "targetPort", job.getTargetPort(),
                    "deploymentType", job.getDeploymentType().name(),
                    "priority", job.getPriority().name(),
                    "oldCertificateId", replacement.getOldCertificate().getId().toString(),
                    "newCertificateId", replacement.getNewCertificate().getId().toString(),
                    "installationId", installation.getId().toString()
            ));

            String entityId = (job.getId() != null) ? job.getId().toString() : job.getJobReference();

            auditService.recordAudit(AuditEvent.create(
                    AuditAction.DEPLOYMENT_JOB_CREATED,
                    "DeploymentJob",
                    entityId,
                    "DeploymentPlanner",
                    "SUCCESS",
                    detailsJson,
                    "127.0.0.1"
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit payload for deployment job reference {}", job.getJobReference(), e);
        }
    }
}
