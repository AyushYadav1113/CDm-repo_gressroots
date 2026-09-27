package com.grassroots.cdm.matching.evaluator.impl;

import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.matching.config.MatchingProperties;
import com.grassroots.cdm.matching.evaluator.MatchSignalEvaluator;
import com.grassroots.cdm.matching.model.MatchReason;
import com.grassroots.cdm.matching.model.MatchSignal;
import com.grassroots.cdm.matching.normalizer.DomainNameNormalizer;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Evaluates Subject Alternative Name (SAN) equivalence, superset coverage,
 * Jaccard similarity, and wildcard containment.
 */
@Component
public class SanMatchEvaluator implements MatchSignalEvaluator {

    private final MatchingProperties properties;

    public SanMatchEvaluator(MatchingProperties properties) {
        this.properties = properties;
    }

    @Override
    public MatchSignal getSignal() {
        return MatchSignal.SAN_MATCH;
    }

    @Override
    public double getWeight() {
        return properties.getSanWeight();
    }

    @Override
    public MatchReason evaluate(CertificateRecord oldCertificate, CertificateRecord candidate) {
        Set<String> oldSans = DomainNameNormalizer.parseAndNormalizeSans(
                oldCertificate.getSubjectAlternativeNames(), oldCertificate.getCommonName());
        Set<String> newSans = DomainNameNormalizer.parseAndNormalizeSans(
                candidate.getSubjectAlternativeNames(), candidate.getCommonName());

        if (oldSans.isEmpty() || newSans.isEmpty()) {
            return MatchReason.of(getSignal(), 0.0, getWeight(), "No SANs or Common Names available to compare");
        }

        // 1. Exact match of normalized SAN sets
        if (oldSans.equals(newSans)) {
            return MatchReason.of(getSignal(), 1.0, getWeight(),
                    "Exact SAN set match: " + oldSans);
        }

        // 2. Candidate is superset (contains all old SANs plus additional new SANs)
        if (newSans.containsAll(oldSans)) {
            return MatchReason.of(getSignal(), 0.90, getWeight(),
                    "Candidate is SAN superset covering all existing names: " + oldSans);
        }

        // 3. Wildcard coverage: every SAN in old is matched by at least one pattern in new
        if (properties.isAllowWildcardExpansion() && allMatchedByWildcard(oldSans, newSans)) {
            return MatchReason.of(getSignal(), 0.85, getWeight(),
                    "All existing SANs covered by candidate wildcard/patterns");
        }

        // 4. Jaccard similarity for partial overlap: |old ∩ new| / |old ∪ new|
        Set<String> intersection = new HashSet<>(oldSans);
        intersection.retainAll(newSans);

        if (!intersection.isEmpty()) {
            Set<String> union = new HashSet<>(oldSans);
            union.addAll(newSans);
            double jaccard = (double) intersection.size() / union.size();
            double rounded = Math.round(jaccard * 100.0) / 100.0;
            return MatchReason.of(getSignal(), rounded, getWeight(),
                    String.format("Partial SAN overlap (%d/%d shared names: %s)",
                            intersection.size(), union.size(), intersection));
        }

        // 5. Completely disjoint SANs
        return MatchReason.of(getSignal(), 0.0, getWeight(),
                "Different SANs: no overlapping domain names between old and candidate");
    }

    private boolean allMatchedByWildcard(Set<String> targets, Set<String> patterns) {
        for (String target : targets) {
            boolean matched = false;
            for (String pattern : patterns) {
                if (DomainNameNormalizer.matchesDomain(pattern, target, true)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }
}
