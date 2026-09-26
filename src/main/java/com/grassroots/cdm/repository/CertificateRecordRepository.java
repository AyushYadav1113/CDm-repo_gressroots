package com.grassroots.cdm.repository;

import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for CertificateRecord entities.
 */
@Repository
public interface CertificateRecordRepository extends JpaRepository<CertificateRecord, UUID> {

    Optional<CertificateRecord> findByExternalId(String externalId);

    Optional<CertificateRecord> findByThumbprint(String thumbprint);

    Optional<CertificateRecord> findByThumbprintIgnoreCase(String thumbprint);

    Optional<CertificateRecord> findByFingerprintSha256(String fingerprintSha256);

    Optional<CertificateRecord> findBySerialNumber(String serialNumber);

    Optional<CertificateRecord> findBySerialNumberAndIssuer(String serialNumber, String issuer);

    List<CertificateRecord> findByCommonName(String commonName);

    List<CertificateRecord> findByStatus(CertificateStatus status);

    List<CertificateRecord> findBySource(CertificateSource source);

    List<CertificateRecord> findByValidToBeforeAndStatus(Instant date, CertificateStatus status);
}
