package com.grassroots.cdm.matching.evaluator;

import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.matching.model.MatchReason;
import com.grassroots.cdm.matching.model.MatchSignal;

/**
 * Interface contract for evaluating a specific heuristic matching signal
 * between an existing certificate and a replacement candidate.
 */
public interface MatchSignalEvaluator {

    /**
     * Identifies the category of signal evaluated.
     */
    MatchSignal getSignal();

    /**
     * Configured relative weight of this signal in the overall score.
     */
    double getWeight();

    /**
     * Evaluates the signal between the old certificate and candidate replacement.
     *
     * @param oldCertificate Existing certificate discovered in ServiceNow
     * @param candidate      New candidate certificate from Sectigo
     * @return MatchReason containing raw score (0.0 to 1.0), weight, weighted score, and explanation
     */
    MatchReason evaluate(CertificateRecord oldCertificate, CertificateRecord candidate);
}
