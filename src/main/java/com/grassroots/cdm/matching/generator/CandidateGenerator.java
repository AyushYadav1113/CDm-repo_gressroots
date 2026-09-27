package com.grassroots.cdm.matching.generator;

import com.grassroots.cdm.entity.CertificateRecord;

import java.util.List;

/**
 * Strategy for filtering, pre-screening, and generating a candidate pool of new certificates
 * eligible for replacing an existing certificate.
 */
public interface CandidateGenerator {

    /**
     * Generates a pre-screened, deduplicated list of candidate replacement certificates.
     *
     * @param oldCertificate The existing certificate to be replaced
     * @param availablePool  All newly issued or available certificates
     * @return Filtered, valid candidate list ready for multi-attribute scoring
     */
    List<CertificateRecord> generateCandidates(CertificateRecord oldCertificate, List<CertificateRecord> availablePool);
}
