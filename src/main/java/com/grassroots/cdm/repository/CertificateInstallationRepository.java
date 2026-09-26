package com.grassroots.cdm.repository;

import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.enums.InstallationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for CertificateInstallation entities.
 */
@Repository
public interface CertificateInstallationRepository extends JpaRepository<CertificateInstallation, UUID> {

    List<CertificateInstallation> findByCertificateId(UUID certificateId);

    List<CertificateInstallation> findByServerId(UUID serverId);

    Optional<CertificateInstallation> findByServerIdAndPortAndBindingInfo(UUID serverId, int port, String bindingInfo);

    List<CertificateInstallation> findByStatus(InstallationStatus status);

    List<CertificateInstallation> findByServerHostname(String hostname);
}
