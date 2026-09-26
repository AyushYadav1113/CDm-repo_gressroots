package com.grassroots.cdm.repository;

import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.ServerStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for TargetServer entities.
 */
@Repository
public interface TargetServerRepository extends JpaRepository<TargetServer, UUID> {

    Optional<TargetServer> findByHostname(String hostname);

    List<TargetServer> findByEnvironment(EnvironmentType environment);

    List<TargetServer> findByTechnology(ServerTechnology technology);

    List<TargetServer> findByStatus(ServerStatus status);

    List<TargetServer> findByMidServerId(UUID midServerId);

    boolean existsByHostname(String hostname);
}
