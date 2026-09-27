package com.grassroots.cdm.matching.evaluator.impl;

import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.matching.config.MatchingProperties;
import com.grassroots.cdm.matching.evaluator.MatchSignalEvaluator;
import com.grassroots.cdm.matching.model.MatchReason;
import com.grassroots.cdm.matching.model.MatchSignal;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Evaluates certificate lifecycle validity windows, extension beyond old expiration,
 * and penalizes expired candidates.
 */
@Component
public class LifecycleEvaluator implements MatchSignalEvaluator {

    private final MatchingProperties properties;

    public LifecycleEvaluator(MatchingProperties properties) {
        this.properties = properties;
    }

    @Override
    public MatchSignal getSignal() {
        return MatchSignal.LIFECYCLE_EXTENSION;
    }

    @Override
    public double getWeight() {
        return properties.getLifecycleWeight();
    }

    @Override
    public MatchReason evaluate(CertificateRecord oldCertificate, CertificateRecord candidate) {
        Instant now = Instant.now();

        // 1. Disqualification check: Candidate is already expired
        if (candidate.getValidTo() != null && candidate.getValidTo().isBefore(now)) {
            return MatchReason.of(getSignal(), 0.0, getWeight(),
                    "Disqualified: candidate certificate is already expired (" + candidate.getValidTo() + ")");
        }

        // 2. Validity extension comparison
        if (candidate.getValidTo() != null && oldCertificate.getValidTo() != null) {
            if (candidate.getValidTo().isAfter(oldCertificate.getValidTo())) {
                return MatchReason.of(getSignal(), 1.0, getWeight(),
                        String.format("Validity extended: candidate expires at %s (current expires %s)",
                                candidate.getValidTo(), oldCertificate.getValidTo()));
            }

            if (candidate.getValidTo().equals(oldCertificate.getValidTo())) {
                return MatchReason.of(getSignal(), 0.30, getWeight(),
                        "Identical validity expiration date: " + candidate.getValidTo());
            }

            // Candidate expires earlier than current certificate
            return MatchReason.of(getSignal(), 0.0, getWeight(),
                    String.format("Candidate expires earlier (%s) than existing certificate (%s)",
                            candidate.getValidTo(), oldCertificate.getValidTo()));
        }

        // 3. Fallback when dates are incomplete on old certificate
        if (candidate.getValidTo() != null && candidate.getValidTo().isAfter(now)) {
            return MatchReason.of(getSignal(), 0.80, getWeight(),
                    "Candidate is currently valid (old validity window unknown)");
        }

        return MatchReason.of(getSignal(), 0.50, getWeight(), "Validity dates partially unspecified");
    }
}
