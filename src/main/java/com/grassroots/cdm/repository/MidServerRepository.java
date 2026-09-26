package com.grassroots.cdm.repository;

import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for MidServer execution agent nodes.
 */
@Repository
public interface MidServerRepository extends JpaRepository<MidServer, UUID> {

    Optional<MidServer> findByName(String name);

    List<MidServerStatus> findByStatus(MidServerStatus status);

    boolean existsByName(String name);
}
