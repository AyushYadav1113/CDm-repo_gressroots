package com.grassroots.cdm.repository;

import com.grassroots.cdm.entity.CertificateReplacement;
import com.grassroots.cdm.entity.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for CertificateReplacement correlation pairs.
 */
@Repository
public interface CertificateReplacementRepository extends JpaRepository<CertificateReplacement, UUID> {

    List<CertificateReplacement> findByOldCertificateId(UUID oldCertificateId);

    List<CertificateReplacement> findByNewCertificateId(UUID newCertificateId);

    Optional<CertificateReplacement> findByOldCertificateIdAndNewCertificateId(UUID oldCertificateId, UUID newCertificateId);

    List<CertificateReplacement> findByMatchStatus(MatchStatus matchStatus);
}
