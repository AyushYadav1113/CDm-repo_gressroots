package com.grassroots.cdm.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer representation of a deployment orchestration job.
 */
public record DeploymentJobDto(
        UUID id,
        String jobReference,
        String idempotencyKey,
        UUID oldCertificateId,
        UUID newCertificateId,
        UUID targetServerId,
        String targetHost,
        int targetPort,
        String targetType,
        String deploymentType,
        String status,
        int attemptCount,
        int maxRetries,
        String errorInformation,
        String midServerTaskId,
        Instant scheduledAt,
        Instant startedAt,
        Instant completedAt
) {}
