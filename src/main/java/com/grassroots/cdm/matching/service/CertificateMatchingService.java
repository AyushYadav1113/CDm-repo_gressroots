package com.grassroots.cdm.matching.service;

import com.grassroots.cdm.entity.CertificateReplacement;
import com.grassroots.cdm.matching.model.CandidateMatchResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service contract for orchestrating certificate matching, creating replacement records,
 * and recording correlation audit trails.
 */
public interface CertificateMatchingService {

    /**
     * Matches a specific old certificate against all available candidate certificates in the repository,
     * persisting a CertificateReplacement record if a match is determined.
     *
     * @param oldCertificateId UUID of the existing certificate
     * @param correlationId    Distributed trace correlation ID
     * @return CandidateMatchResult detailing score, decision, and ranked candidates
     */
    CandidateMatchResult matchCertificate(UUID oldCertificateId, String correlationId);

    /**
     * Scans and matches all expiring certificates in the repository against available replacement candidates.
     *
     * @param correlationId Distributed trace correlation ID
     * @return List of matching results
     */
    List<CandidateMatchResult> matchAllExpiringCertificates(String correlationId);

    /**
     * Retrieves an existing replacement record for a given old certificate if one has been established.
     *
     * @param oldCertificateId UUID of the existing certificate
     * @return Optional CertificateReplacement entity
     */
    Optional<CertificateReplacement> getReplacementForOldCertificate(UUID oldCertificateId);

    default CandidateMatchResult matchCertificate(UUID oldCertificateId) {
        return matchCertificate(oldCertificateId, null);
    }

    default List<CandidateMatchResult> matchAllExpiringCertificates() {
        return matchAllExpiringCertificates(null);
    }
}
