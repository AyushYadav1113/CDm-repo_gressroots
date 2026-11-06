package com.grassroots.cdm.service;

import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.dto.DeploymentJobDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.enums.DeploymentType;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for querying and operating on deployment orchestration jobs.
 */
public interface DeploymentService {

    /**
     * Retrieves paginated deployment jobs matching optional filter criteria.
     */
    PagedResponse<DeploymentJobDto> getDeployments(
            DeploymentJobStatus status,
            String targetHost,
            DeploymentType deploymentType,
            Pageable pageable
    );

    /**
     * Retrieves a single deployment job by its unique UUID identifier.
     *
     * @param id deployment job UUID
     * @return deployment job DTO
     * @throws com.grassroots.cdm.exception.ResourceNotFoundException if no deployment job exists with the ID
     */
    DeploymentJobDto getDeploymentById(UUID id);

    /**
     * Manually triggers a retry execution for a failed or stuck deployment job.
     *
     * @param id            deployment job UUID
     * @param correlationId optional tracing correlation ID
     * @return updated deployment job DTO
     */
    DeploymentJobDto retryDeployment(UUID id, String correlationId);

    /**
     * Manually triggers live TLS verification for a deployed certificate.
     *
     * @param id            deployment job UUID
     * @param correlationId optional tracing correlation ID
     * @return updated deployment job DTO
     */
    DeploymentJobDto verifyDeployment(UUID id, String correlationId);
}
