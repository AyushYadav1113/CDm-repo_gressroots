package com.grassroots.cdm.repository;

import com.grassroots.cdm.entity.DeploymentJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for DeploymentJob entities.
 */
@Repository
public interface DeploymentJobRepository extends JpaRepository<DeploymentJob, UUID> {

    Optional<DeploymentJob> findByJobReference(String jobReference);

    List<DeploymentJob> findByStatus(String status);

    List<DeploymentJob> findByTargetHost(String targetHost);
}
