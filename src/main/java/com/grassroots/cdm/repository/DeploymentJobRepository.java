package com.grassroots.cdm.repository;

import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.enums.DeploymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for DeploymentJob entities.
 */
@Repository
public interface DeploymentJobRepository extends JpaRepository<DeploymentJob, UUID> {

    Optional<DeploymentJob> findByJobReference(String jobReference);

    Optional<DeploymentJob> findByIdempotencyKey(String idempotencyKey);

    List<DeploymentJob> findByStatus(DeploymentJobStatus status);

    List<DeploymentJob> findByStatusIn(Collection<DeploymentJobStatus> statuses);

    List<DeploymentJob> findByTargetHost(String targetHost);

    List<DeploymentJob> findByTargetServerId(UUID targetServerId);

    List<DeploymentJob> findByNewCertificateId(UUID newCertificateId);

    List<DeploymentJob> findByDeploymentType(DeploymentType deploymentType);

    List<DeploymentJob> findByInstallationId(UUID installationId);

    List<DeploymentJob> findByOldCertificateId(UUID oldCertificateId);

    Optional<DeploymentJob> findByInstallationIdAndNewCertificateId(UUID installationId, UUID newCertificateId);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
