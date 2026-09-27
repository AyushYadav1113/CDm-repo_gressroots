package com.grassroots.cdm.deployment.workflow.statemachine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.deployment.workflow.exception.InvalidStateTransitionException;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of DeploymentStateMachine.
 * Strictly validates and enforces valid state transitions according to CDM deployment lifecycle rules.
 */
@Component
public class DefaultDeploymentStateMachine implements DeploymentStateMachine {

    private static final Logger log = LoggerFactory.getLogger(DefaultDeploymentStateMachine.class);

    private static final Map<DeploymentJobStatus, Set<DeploymentJobStatus>> TRANSITION_MAP = new EnumMap<>(DeploymentJobStatus.class);

    static {
        // CREATED
        TRANSITION_MAP.put(DeploymentJobStatus.CREATED, Set.of(
                DeploymentJobStatus.PLANNED,
                DeploymentJobStatus.FAILED,
                DeploymentJobStatus.CANCELLED
        ));

        // PLANNED (and legacy PENDING)
        TRANSITION_MAP.put(DeploymentJobStatus.PLANNED, Set.of(
                DeploymentJobStatus.CREDENTIALS_PENDING,
                DeploymentJobStatus.FAILED,
                DeploymentJobStatus.MANUAL_REVIEW,
                DeploymentJobStatus.CANCELLED
        ));
        TRANSITION_MAP.put(DeploymentJobStatus.PENDING, Set.of(
                DeploymentJobStatus.PLANNED,
                DeploymentJobStatus.CREDENTIALS_PENDING,
                DeploymentJobStatus.IN_PROGRESS,
                DeploymentJobStatus.SENT_TO_MID,
                DeploymentJobStatus.FAILED,
                DeploymentJobStatus.MANUAL_REVIEW,
                DeploymentJobStatus.CANCELLED
        ));

        // CREDENTIALS_PENDING
        TRANSITION_MAP.put(DeploymentJobStatus.CREDENTIALS_PENDING, Set.of(
                DeploymentJobStatus.CREDENTIALS_ACQUIRED,
                DeploymentJobStatus.FAILED,
                DeploymentJobStatus.RETRY_PENDING,
                DeploymentJobStatus.MANUAL_REVIEW
        ));

        // CREDENTIALS_ACQUIRED (and legacy CREDENTIAL_ACQUIRED)
        TRANSITION_MAP.put(DeploymentJobStatus.CREDENTIALS_ACQUIRED, Set.of(
                DeploymentJobStatus.SENT_TO_MID,
                DeploymentJobStatus.FAILED,
                DeploymentJobStatus.RETRY_PENDING,
                DeploymentJobStatus.MANUAL_REVIEW
        ));
        TRANSITION_MAP.put(DeploymentJobStatus.CREDENTIAL_ACQUIRED, Set.of(
                DeploymentJobStatus.SENT_TO_MID,
                DeploymentJobStatus.DISPATCHED,
                DeploymentJobStatus.FAILED
        ));

        // SENT_TO_MID (and legacy DISPATCHED)
        TRANSITION_MAP.put(DeploymentJobStatus.SENT_TO_MID, Set.of(
                DeploymentJobStatus.RUNNING,
                DeploymentJobStatus.FAILED,
                DeploymentJobStatus.RETRY_PENDING,
                DeploymentJobStatus.MANUAL_REVIEW
        ));
        TRANSITION_MAP.put(DeploymentJobStatus.DISPATCHED, Set.of(
                DeploymentJobStatus.RUNNING,
                DeploymentJobStatus.IN_PROGRESS,
                DeploymentJobStatus.FAILED
        ));

        // RUNNING (and legacy IN_PROGRESS)
        TRANSITION_MAP.put(DeploymentJobStatus.RUNNING, Set.of(
                DeploymentJobStatus.DEPLOYED,
                DeploymentJobStatus.FAILED,
                DeploymentJobStatus.RETRY_PENDING,
                DeploymentJobStatus.MANUAL_REVIEW
        ));
        TRANSITION_MAP.put(DeploymentJobStatus.IN_PROGRESS, Set.of(
                DeploymentJobStatus.DEPLOYED,
                DeploymentJobStatus.VERIFYING,
                DeploymentJobStatus.COMPLETED,
                DeploymentJobStatus.FAILED
        ));

        // DEPLOYED
        TRANSITION_MAP.put(DeploymentJobStatus.DEPLOYED, Set.of(
                DeploymentJobStatus.VERIFICATION_PENDING,
                DeploymentJobStatus.VERIFIED,
                DeploymentJobStatus.FAILED,
                DeploymentJobStatus.RETRY_PENDING,
                DeploymentJobStatus.MANUAL_REVIEW
        ));

        // VERIFICATION_PENDING (and legacy VERIFYING)
        TRANSITION_MAP.put(DeploymentJobStatus.VERIFICATION_PENDING, Set.of(
                DeploymentJobStatus.VERIFIED,
                DeploymentJobStatus.FAILED,
                DeploymentJobStatus.RETRY_PENDING,
                DeploymentJobStatus.MANUAL_REVIEW
        ));
        TRANSITION_MAP.put(DeploymentJobStatus.VERIFYING, Set.of(
                DeploymentJobStatus.VERIFIED,
                DeploymentJobStatus.COMPLETED,
                DeploymentJobStatus.FAILED
        ));

        // VERIFIED
        TRANSITION_MAP.put(DeploymentJobStatus.VERIFIED, Set.of(
                DeploymentJobStatus.COMPLETED,
                DeploymentJobStatus.FAILED,
                DeploymentJobStatus.MANUAL_REVIEW
        ));

        // FAILED
        TRANSITION_MAP.put(DeploymentJobStatus.FAILED, Set.of(
                DeploymentJobStatus.RETRY_PENDING,
                DeploymentJobStatus.MANUAL_REVIEW,
                DeploymentJobStatus.CANCELLED
        ));

        // RETRY_PENDING
        TRANSITION_MAP.put(DeploymentJobStatus.RETRY_PENDING, Set.of(
                DeploymentJobStatus.CREDENTIALS_PENDING,
                DeploymentJobStatus.SENT_TO_MID,
                DeploymentJobStatus.MANUAL_REVIEW,
                DeploymentJobStatus.FAILED,
                DeploymentJobStatus.CANCELLED
        ));

        // MANUAL_REVIEW
        TRANSITION_MAP.put(DeploymentJobStatus.MANUAL_REVIEW, Set.of(
                DeploymentJobStatus.RETRY_PENDING,
                DeploymentJobStatus.CREDENTIALS_PENDING,
                DeploymentJobStatus.COMPLETED,
                DeploymentJobStatus.FAILED,
                DeploymentJobStatus.CANCELLED
        ));

        // Terminal states (no transitions permitted)
        TRANSITION_MAP.put(DeploymentJobStatus.COMPLETED, Collections.emptySet());
        TRANSITION_MAP.put(DeploymentJobStatus.CANCELLED, Collections.emptySet());
        TRANSITION_MAP.put(DeploymentJobStatus.ROLLED_BACK, Collections.emptySet());
    }

    private final DeploymentJobRepository deploymentJobRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public DefaultDeploymentStateMachine(
            DeploymentJobRepository deploymentJobRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.deploymentJobRepository = deploymentJobRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean canTransition(DeploymentJobStatus from, DeploymentJobStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true; // Idempotent no-op
        }
        Set<DeploymentJobStatus> allowed = TRANSITION_MAP.get(from);
        return allowed != null && allowed.contains(to);
    }

    @Override
    public Set<DeploymentJobStatus> getValidNextStates(DeploymentJobStatus current) {
        if (current == null) {
            return Collections.emptySet();
        }
        return TRANSITION_MAP.getOrDefault(current, Collections.emptySet());
    }

    @Override
    @Transactional
    public DeploymentJob transition(DeploymentJob job, DeploymentJobStatus targetStatus, String reason, String actor, String correlationId) {
        if (job == null) {
            throw new IllegalArgumentException("DeploymentJob must not be null");
        }
        if (targetStatus == null) {
            throw new IllegalArgumentException("Target DeploymentJobStatus must not be null");
        }

        DeploymentJobStatus currentStatus = job.getStatus();

        // Idempotent check: if already in the target status, return existing without error
        if (currentStatus == targetStatus) {
            log.info("Job {} is already in status {}. Idempotent transition ignored.", job.getJobReference(), targetStatus);
            return job;
        }

        // Validate transition
        if (!canTransition(currentStatus, targetStatus)) {
            throw new InvalidStateTransitionException(
                    job.getId(),
                    currentStatus,
                    targetStatus,
                    "Permitted next states from " + currentStatus + " are: " + getValidNextStates(currentStatus)
            );
        }

        // Update timestamps based on target lifecycle status
        applyLifecycleTimestamps(job, targetStatus);

        // Update status
        job.setStatus(targetStatus);

        if (reason != null && !reason.isBlank()) {
            if (targetStatus == DeploymentJobStatus.FAILED) {
                job.setErrorMessage(reason);
            }
        }

        // Persist state change
        DeploymentJob savedJob = deploymentJobRepository.saveAndFlush(job);
        log.info("Transitioned DeploymentJob {} ({}) from {} to {} [reason='{}', actor='{}']",
                savedJob.getJobReference(), savedJob.getId(), currentStatus, targetStatus, reason, actor);

        // Record audit event
        recordTransitionAudit(savedJob, currentStatus, targetStatus, reason, actor, resolveCorrelationId(correlationId));

        return savedJob;
    }

    @Override
    public DeploymentJob transition(DeploymentJob job, DeploymentJobStatus targetStatus, String reason, String actor) {
        return transition(job, targetStatus, reason, actor, null);
    }

    private void applyLifecycleTimestamps(DeploymentJob job, DeploymentJobStatus targetStatus) {
        Instant now = Instant.now();
        switch (targetStatus) {
            case SENT_TO_MID, DISPATCHED -> {
                if (job.getDispatchedAt() == null) {
                    job.setDispatchedAt(now);
                }
            }
            case RUNNING, IN_PROGRESS -> {
                if (job.getStartedAt() == null) {
                    job.setStartedAt(now);
                }
            }
            case COMPLETED -> {
                if (job.getCompletedAt() == null) {
                    job.setCompletedAt(now);
                }
            }
            case RETRY_PENDING -> {
                job.setRetryCount(job.getRetryCount() + 1);
            }
            default -> {}
        }
    }

    private void recordTransitionAudit(
            DeploymentJob job,
            DeploymentJobStatus from,
            DeploymentJobStatus to,
            String reason,
            String actor,
            String correlationId
    ) {
        AuditAction action = resolveAuditAction(to);
        String entityId = job.getId() != null ? job.getId().toString() : job.getJobReference();

        try {
            String detailsJson = objectMapper.writeValueAsString(Map.of(
                    "jobReference", job.getJobReference(),
                    "idempotencyKey", job.getIdempotencyKey(),
                    "fromStatus", from.name(),
                    "toStatus", to.name(),
                    "reason", reason != null ? reason : "State machine transition",
                    "actor", actor != null ? actor : "SYSTEM",
                    "correlationId", correlationId
            ));

            auditService.recordAudit(AuditEvent.create(
                    action,
                    "DeploymentJob",
                    entityId,
                    actor != null ? actor : "SYSTEM",
                    to == DeploymentJobStatus.FAILED ? "FAILURE" : "SUCCESS",
                    detailsJson,
                    "127.0.0.1"
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize transition audit payload for job {}", entityId, e);
        }
    }

    private AuditAction resolveAuditAction(DeploymentJobStatus status) {
        return switch (status) {
            case CREDENTIALS_ACQUIRED, CREDENTIAL_ACQUIRED -> AuditAction.CREDENTIAL_RETRIEVED;
            case SENT_TO_MID, DISPATCHED -> AuditAction.DEPLOYMENT_DISPATCHED;
            case VERIFIED -> AuditAction.LIVE_ENDPOINT_VERIFIED;
            case FAILED -> AuditAction.JOB_FAILED;
            case RETRY_PENDING -> AuditAction.JOB_RETRIED;
            default -> AuditAction.DEPLOYMENT_JOB_CREATED;
        };
    }

    private String resolveCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        String mdc = MDC.get("correlationId");
        if (mdc != null && !mdc.isBlank()) {
            return mdc;
        }
        return UUID.randomUUID().toString();
    }
}
