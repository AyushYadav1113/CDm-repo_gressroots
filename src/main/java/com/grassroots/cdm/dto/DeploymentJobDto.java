package com.grassroots.cdm.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Deployment job boundary transfer representation.
 */
public record DeploymentJobDto(
        UUID id,
        String jobReference,
        UUID oldCertificateId,
        UUID newCertificateId,
        String targetHost,
        int targetPort,
        String targetType,
        String status,
        int retryCount,
        int maxRetries,
        String errorMessage,
        Instant scheduledAt,
        Instant dispatchedAt,
        Instant completedAt
) {}
