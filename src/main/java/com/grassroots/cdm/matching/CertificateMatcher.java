package com.grassroots.cdm.matching;

import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.matching.model.CandidateMatchResult;

import java.util.List;

/**
 * Strategy interface for correlating existing deployed certificates
 * to newly issued or renewed certificates from Sectigo using multi-attribute scoring.
 */
public interface CertificateMatcher {

    /**
     * Evaluates an old certificate against candidate certificates and determines
     * the best replacement match, score breakdown, and confidence decision.
     *
     * @param oldCertificate       Existing certificate discovered in ServiceNow
     * @param candidateCertificates Pool of newly issued candidate certificates from Sectigo
     * @return Comprehensive CandidateMatchResult containing decision, score, and ranked candidates
     */
    CandidateMatchResult match(CertificateRecord oldCertificate, List<CertificateRecord> candidateCertificates);

    /**
     * Backward-compatible evaluation method returning legacy MatchResult record.
     *
     * @param candidateReplacement Newly issued certificate from Sectigo
     * @param existingCertificates Pool of discovered existing certificates from ServiceNow
     * @return MatchResult containing outcome, confidence, and matched entity if found
     */
    default MatchResult matchCertificate(CertificateRecord candidateReplacement, List<CertificateRecord> existingCertificates) {
        CandidateMatchResult result = match(candidateReplacement, existingCertificates);
        return new MatchResult(
                result.matchedCandidate().isPresent(),
                result.matchedCandidate(),
                "MULTI_ATTRIBUTE_SCORING",
                result.matchScore(),
                result.decisionSummary()
        );
    }
}
