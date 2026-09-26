package com.grassroots.cdm.repository;

import com.grassroots.cdm.entity.CertificateRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for CertificateRecord entities.
 */
@Repository
public interface CertificateRecordRepository extends JpaRepository<CertificateRecord, UUID> {

    Optional<CertificateRecord> findByFingerprintSha256(String fingerprintSha256);

    Optional<CertificateRecord> findBySerialNumber(String serialNumber);

    List<CertificateRecord> findByCommonName(String commonName);

    List<CertificateRecord> findByStatus(String status);
}
