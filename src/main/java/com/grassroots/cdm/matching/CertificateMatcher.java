package com.grassroots.cdm.matching;

import com.grassroots.cdm.entity.CertificateRecord;

import java.util.List;

/**
 * Strategy interface for correlating existing deployed certificates
 * to newly issued or renewed certificates from Sectigo.
 */
public interface CertificateMatcher {

    /**
     * Attempts to find a matching old certificate for a given candidate replacement.
     *
     * @param candidateReplacement Newly issued certificate from Sectigo
     * @param existingCertificates Pool of discovered existing certificates from ServiceNow
     * @return MatchResult containing outcome, confidence, and matched entity if found
     */
    MatchResult matchCertificate(CertificateRecord candidateReplacement, List<CertificateRecord> existingCertificates);
}
