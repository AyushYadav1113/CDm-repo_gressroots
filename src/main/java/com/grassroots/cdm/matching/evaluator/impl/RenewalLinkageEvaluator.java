package com.grassroots.cdm.matching.evaluator.impl;

import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.matching.config.MatchingProperties;
import com.grassroots.cdm.matching.evaluator.MatchSignalEvaluator;
import com.grassroots.cdm.matching.model.MatchReason;
import com.grassroots.cdm.matching.model.MatchSignal;
import org.springframework.stereotype.Component;

/**
 * Evaluates explicit CA renewal order linkage or external identifier cross-referencing.
 */
@Component
public class RenewalLinkageEvaluator implements MatchSignalEvaluator {

    private final MatchingProperties properties;

    public RenewalLinkageEvaluator(MatchingProperties properties) {
        this.properties = properties;
    }

    @Override
    public MatchSignal getSignal() {
        return MatchSignal.RENEWAL_LINKAGE;
    }

    @Override
    public double getWeight() {
        return properties.getRenewalLinkageWeight();
    }

    @Override
    public MatchReason evaluate(CertificateRecord oldCertificate, CertificateRecord candidate) {
        // 1. Check if external IDs match directly (e.g. same ServiceNow sys_id or orderId)
        if (candidate.getExternalId() != null && !candidate.getExternalId().isBlank()
                && candidate.getExternalId().equalsIgnoreCase(oldCertificate.getExternalId())) {
            return MatchReason.of(getSignal(), 1.0, getWeight(),
                    "Explicit CA renewal linkage: identical external identifier " + candidate.getExternalId());
        }

        // 2. Check if candidate explicitly declares predecessor reference
        // (e.g. Sectigo renewedFromCertificateId or replacement pointer)
        if (candidate.getExternalId() != null && oldCertificate.getExternalId() != null) {
            String candidateId = candidate.getExternalId().trim();
            String oldId = oldCertificate.getExternalId().trim();

            if (candidateId.contains(oldId) || oldId.contains(candidateId)) {
                return MatchReason.of(getSignal(), 0.80, getWeight(),
                        String.format("Correlated external ID reference: '%s' <-> '%s'", oldId, candidateId));
            }
        }

        return MatchReason.of(getSignal(), 0.0, getWeight(),
                "No explicit CA renewal linkage declared");
    }
}
