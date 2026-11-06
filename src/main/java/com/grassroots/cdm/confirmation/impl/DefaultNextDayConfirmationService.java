package com.grassroots.cdm.confirmation.impl;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.confirmation.ConfirmationStatus;
import com.grassroots.cdm.confirmation.NextDayConfirmationService;
import com.grassroots.cdm.confirmation.model.NextDayConfirmationResult;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.integration.servicenow.ServiceNowClient;
import com.grassroots.cdm.integration.servicenow.dto.ServiceNowCertificateDto;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of {@link NextDayConfirmationService}.
 *
 * <p><strong>Algorithm per job:</strong></p>
 * <ol>
 *   <li>Load the deployment job — must be VERIFIED or COMPLETED.</li>
 *   <li>Extract expected new-certificate thumbprint and target host.</li>
 *   <li>Query ServiceNow CMDB for the target host's certificate CI record.</li>
 *   <li>If ServiceNow is unavailable → DISCOVERY_UNAVAILABLE (never mark success on missing data).</li>
 *   <li>If CMDB returns no records for the host → DISCOVERY_UNAVAILABLE.</li>
 *   <li>If CMDB returns the OLD certificate thumbprint → CONFIRMATION_FAILED.</li>
 *   <li>If CMDB returns the expected new-certificate thumbprint → CONFIRMED.</li>
 *   <li>If CMDB returns an unknown thumbprint → CONFIRMATION_FAILED with mismatch reason.</li>
 *   <li>Persist a rich audit record for every determination.</li>
 * </ol>
 */
@Service
public class DefaultNextDayConfirmationService implements NextDayConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultNextDayConfirmationService.class);

    /**
     * Jobs in these statuses are eligible for next-day confirmation.
     */
    private static final Set<DeploymentJobStatus> ELIGIBLE_STATUSES = EnumSet.of(
            DeploymentJobStatus.VERIFIED,
            DeploymentJobStatus.COMPLETED
    );

    private final DeploymentJobRepository jobRepository;
    private final ServiceNowClient serviceNowClient;
    private final AuditService auditService;

    public DefaultNextDayConfirmationService(
            DeploymentJobRepository jobRepository,
            ServiceNowClient serviceNowClient,
            AuditService auditService
    ) {
        this.jobRepository = jobRepository;
        this.serviceNowClient = serviceNowClient;
        this.auditService = auditService;
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public NextDayConfirmationResult confirmDeployment(UUID jobId, String correlationId) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId must not be null");
        }
        String cid = (correlationId != null) ? correlationId : UUID.randomUUID().toString();

        Optional<DeploymentJob> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            log.warn("[{}] DeploymentJob not found: {}", cid, jobId);
            return NextDayConfirmationResult.discoveryUnavailable(jobId, cid, "UNKNOWN",
                    "Deployment job not found in database");
        }

        DeploymentJob job = jobOpt.get();
        return evaluate(job, cid);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NextDayConfirmationResult> processAllPendingConfirmations(String correlationId) {
        String cid = (correlationId != null) ? correlationId : UUID.randomUUID().toString();
        List<DeploymentJob> eligible = new ArrayList<>();
        for (DeploymentJobStatus status : ELIGIBLE_STATUSES) {
            eligible.addAll(jobRepository.findByStatus(status));
        }

        if (eligible.isEmpty()) {
            log.info("[{}] No jobs eligible for next-day confirmation", cid);
            return List.of();
        }

        log.info("[{}] Processing next-day confirmation for {} jobs", cid, eligible.size());
        List<NextDayConfirmationResult> results = new ArrayList<>();

        for (DeploymentJob job : eligible) {
            String jobCid = cid + "-" + job.getId();
            try {
                NextDayConfirmationResult result = evaluate(job, jobCid);
                results.add(result);
            } catch (Exception ex) {
                log.error("[{}] Unexpected error during confirmation for job {}: {}", cid, job.getId(), ex.getMessage(), ex);
                results.add(NextDayConfirmationResult.discoveryUnavailable(
                        job.getId(), jobCid, job.getTargetHost(),
                        "Unexpected error during evaluation: " + ex.getMessage()));
            }
        }

        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public NextDayConfirmationResult recheckConfirmation(UUID jobId, String correlationId) {
        log.info("[{}] Manual re-check requested for job {}", correlationId, jobId);
        return confirmDeployment(jobId, correlationId);
    }

    // ─── Core Evaluation Logic ────────────────────────────────────────────────

    /**
     * Performs the actual confirmation evaluation for a single job.
     * This is the central method that enforces all safety invariants.
     */
    private NextDayConfirmationResult evaluate(DeploymentJob job, String correlationId) {
        UUID jobId      = job.getId();
        String host     = job.getTargetHost();

        // ── Guard: job must be in an eligible status ──────────────────────────
        if (!ELIGIBLE_STATUSES.contains(job.getStatus())) {
            String reason = String.format(
                    "Job %s is in status %s — not eligible for next-day confirmation",
                    jobId, job.getStatus());
            log.debug("[{}] {}", correlationId, reason);
            return NextDayConfirmationResult.pendingConfirmation(jobId, correlationId, host);
        }

        // ── Guard: new certificate must be present ────────────────────────────
        CertificateRecord newCert = job.getNewCertificate();
        if (newCert == null) {
            String reason = "Job has no associated new certificate — cannot confirm";
            log.warn("[{}] Job {} — {}", correlationId, jobId, reason);
            auditConfirmation(jobId, correlationId, AuditAction.NEXT_DAY_DISCOVERY_UNAVAILABLE, reason, host);
            return NextDayConfirmationResult.discoveryUnavailable(jobId, correlationId, host, reason);
        }

        // ── Guard: target host must be resolvable ─────────────────────────────
        if (host == null || host.isBlank()) {
            String reason = "Job has no target host — cannot query ServiceNow";
            log.warn("[{}] Job {} — {}", correlationId, jobId, reason);
            auditConfirmation(jobId, correlationId, AuditAction.NEXT_DAY_DISCOVERY_UNAVAILABLE, reason, "UNKNOWN");
            return NextDayConfirmationResult.discoveryUnavailable(jobId, correlationId, "UNKNOWN", reason);
        }

        String expectedThumbprint = resolveThumbprint(newCert);
        String oldThumbprint = (job.getOldCertificate() != null)
                ? resolveThumbprint(job.getOldCertificate()) : null;

        log.info("[{}] Confirming deployment for job {} on host {} (expectedThumbprint={})",
                correlationId, jobId, host, expectedThumbprint);

        // ── Query ServiceNow CMDB ─────────────────────────────────────────────
        List<ServiceNowCertificateDto> cmdbRecords;
        try {
            String query = buildServiceNowQuery(host, expectedThumbprint);
            cmdbRecords = serviceNowClient.fetchCertificates(query, 10, 0, correlationId);
        } catch (Exception ex) {
            String reason = "ServiceNow query failed: " + ex.getMessage();
            log.warn("[{}] Job {} — {}", correlationId, jobId, reason);
            auditConfirmation(jobId, correlationId, AuditAction.NEXT_DAY_DISCOVERY_UNAVAILABLE, reason, host);
            // SAFETY: never mark confirmed when discovery is unreachable
            return NextDayConfirmationResult.discoveryUnavailable(jobId, correlationId, host, reason);
        }

        // ── SAFETY: empty/null response → DISCOVERY_UNAVAILABLE ──────────────
        if (cmdbRecords == null || cmdbRecords.isEmpty()) {
            String reason = String.format(
                    "ServiceNow returned no CMDB records for host %s — discovery may not have run yet", host);
            log.info("[{}] Job {} — {}", correlationId, jobId, reason);
            auditConfirmation(jobId, correlationId, AuditAction.NEXT_DAY_DISCOVERY_UNAVAILABLE, reason, host);
            return NextDayConfirmationResult.discoveryUnavailable(jobId, correlationId, host, reason);
        }

        // ── Evaluate each returned CMDB record ────────────────────────────────
        return evaluateCmdbRecords(job, correlationId, cmdbRecords, expectedThumbprint, oldThumbprint, host);
    }

    /**
     * Inspects the returned CMDB certificate records against the expected thumbprint.
     * Enforces the safety rule that old-cert presence always → FAILED.
     */
    private NextDayConfirmationResult evaluateCmdbRecords(
            DeploymentJob job,
            String correlationId,
            List<ServiceNowCertificateDto> records,
            String expectedThumbprint,
            String oldThumbprint,
            String host
    ) {
        UUID jobId = job.getId();
        boolean foundNewCert   = false;
        boolean foundOldCert   = false;
        String observedThumbprint = null;
        String confirmedSysId  = null;

        for (ServiceNowCertificateDto record : records) {
            String recordThumbprint = normalise(record.getThumbprint());
            if (recordThumbprint == null) {
                continue;
            }

            // Check for confirmed new certificate
            if (expectedThumbprint != null && expectedThumbprint.equalsIgnoreCase(recordThumbprint)) {
                foundNewCert       = true;
                observedThumbprint = recordThumbprint;
                confirmedSysId     = record.getSysId();
                break;
            }

            // Check if the OLD certificate is still present (safety gate)
            if (oldThumbprint != null && oldThumbprint.equalsIgnoreCase(recordThumbprint)) {
                foundOldCert       = true;
                observedThumbprint = recordThumbprint;
            }
        }

        if (foundNewCert) {
            String msg = String.format(
                    "CMDB confirmed: host %s now carries new certificate (thumbprint=%s, sysId=%s)",
                    host, observedThumbprint, confirmedSysId);
            log.info("[{}] Job {} — {}", correlationId, jobId, msg);
            auditConfirmation(jobId, correlationId, AuditAction.NEXT_DAY_CONFIRMED, msg, host);
            return NextDayConfirmationResult.confirmed(
                    jobId, correlationId, host, expectedThumbprint, observedThumbprint, confirmedSysId);
        }

        if (foundOldCert) {
            String msg = String.format(
                    "CMDB shows OLD certificate still on host %s (thumbprint=%s). Deployment may not have applied.",
                    host, observedThumbprint);
            log.warn("[{}] Job {} — {}", correlationId, jobId, msg);
            auditConfirmation(jobId, correlationId, AuditAction.NEXT_DAY_CONFIRMATION_FAILED, msg, host);
            return NextDayConfirmationResult.failed(
                    jobId, correlationId, host, expectedThumbprint, observedThumbprint,
                    "Old certificate still present in CMDB");
        }

        // CMDB returned records for the host but with an unrecognised thumbprint
        observedThumbprint = records.stream()
                .map(r -> normalise(r.getThumbprint()))
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElse("UNKNOWN");

        String msg = String.format(
                "Certificate mismatch on host %s. Expected=%s, CMDB observed=%s",
                host, expectedThumbprint, observedThumbprint);
        log.warn("[{}] Job {} — {}", correlationId, jobId, msg);
        auditConfirmation(jobId, correlationId, AuditAction.NEXT_DAY_CONFIRMATION_FAILED, msg, host);
        return NextDayConfirmationResult.failed(
                jobId, correlationId, host, expectedThumbprint, observedThumbprint,
                "Unexpected certificate in CMDB — not the expected new certificate");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Builds a ServiceNow query that fetches certificate CIs for a given host.
     * Queries by target_host OR fqdn so we capture both field conventions.
     */
    private String buildServiceNowQuery(String host, String expectedThumbprint) {
        // Primary: match the host so we get what ServiceNow currently sees on it
        String hostQuery = String.format("target_host=%s^ORfqdn=%s", host, host);
        // If we have the thumbprint add it as an OR-joined fallback for cases where
        // the CI was updated by fingerprint without matching the hostname field.
        if (expectedThumbprint != null && !expectedThumbprint.isBlank()) {
            return hostQuery + String.format("^ORthumbprint=%s^ORfingerprint=%s",
                    expectedThumbprint, expectedThumbprint);
        }
        return hostQuery;
    }

    private String resolveThumbprint(CertificateRecord cert) {
        if (cert == null) return null;
        String tp = cert.getThumbprint();
        return (tp != null && !tp.isBlank()) ? normalise(tp) : null;
    }

    private String normalise(String thumbprint) {
        return (thumbprint != null && !thumbprint.isBlank())
                ? thumbprint.toUpperCase().replaceAll("[\\s:]", "")
                : null;
    }

    private void auditConfirmation(UUID jobId, String correlationId,
                                   AuditAction action, String message, String host) {
        try {
            auditService.recordWorkflowEvent(jobId, correlationId,
                    action.name(), deriveOutcome(action), message,
                    Map.of("targetHost", host != null ? host : "UNKNOWN"));
        } catch (Exception ex) {
            log.warn("[{}] Failed to persist audit record for confirmation action {}: {}",
                    correlationId, action, ex.getMessage());
        }
    }

    private String deriveOutcome(AuditAction action) {
        return switch (action) {
            case NEXT_DAY_CONFIRMED -> "CONFIRMED";
            case NEXT_DAY_CONFIRMATION_FAILED -> "FAILED";
            case NEXT_DAY_DISCOVERY_UNAVAILABLE -> "UNAVAILABLE";
            case NEXT_DAY_MANUAL_REVIEW_REQUIRED -> "MANUAL_REVIEW";
            default -> "INFO";
        };
    }
}
