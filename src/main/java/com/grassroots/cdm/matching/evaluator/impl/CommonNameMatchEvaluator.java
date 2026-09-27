package com.grassroots.cdm.matching.evaluator.impl;

import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.matching.config.MatchingProperties;
import com.grassroots.cdm.matching.evaluator.MatchSignalEvaluator;
import com.grassroots.cdm.matching.model.MatchReason;
import com.grassroots.cdm.matching.model.MatchSignal;
import com.grassroots.cdm.matching.normalizer.DomainNameNormalizer;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Evaluates Common Name (CN) matching, handling DNS case normalization,
 * whitespace, trailing dots, and wildcard equivalence.
 */
@Component
public class CommonNameMatchEvaluator implements MatchSignalEvaluator {

    private final MatchingProperties properties;

    public CommonNameMatchEvaluator(MatchingProperties properties) {
        this.properties = properties;
    }

    @Override
    public MatchSignal getSignal() {
        return MatchSignal.COMMON_NAME_MATCH;
    }

    @Override
    public double getWeight() {
        return properties.getCnWeight();
    }

    @Override
    public MatchReason evaluate(CertificateRecord oldCertificate, CertificateRecord candidate) {
        String oldCn = DomainNameNormalizer.normalizeDomain(oldCertificate.getCommonName());
        String newCn = DomainNameNormalizer.normalizeDomain(candidate.getCommonName());

        if (oldCn.isEmpty() || newCn.isEmpty()) {
            return MatchReason.of(getSignal(), 0.0, getWeight(), "Common Name missing on one or both certificates");
        }

        // 1. Exact match after DNS normalization
        if (oldCn.equals(newCn)) {
            return MatchReason.of(getSignal(), 1.0, getWeight(), "Exact Common Name match: " + oldCn);
        }

        // 2. Wildcard match (e.g. *.example.com vs api.example.com)
        if (properties.isAllowWildcardExpansion()) {
            if (DomainNameNormalizer.matchesWildcard(newCn, oldCn)) {
                return MatchReason.of(getSignal(), 0.80, getWeight(),
                        String.format("Candidate wildcard CN '%s' covers existing CN '%s'", newCn, oldCn));
            }
            if (DomainNameNormalizer.matchesWildcard(oldCn, newCn)) {
                return MatchReason.of(getSignal(), 0.80, getWeight(),
                        String.format("Candidate CN '%s' covered by existing wildcard CN '%s'", newCn, oldCn));
            }
        }

        // 3. Fallback: Old CN exists in candidate's SANs, or New CN exists in old SANs
        Set<String> newSans = DomainNameNormalizer.parseAndNormalizeSans(
                candidate.getSubjectAlternativeNames(), candidate.getCommonName());
        if (newSans.contains(oldCn)) {
            return MatchReason.of(getSignal(), 0.70, getWeight(),
                    String.format("Existing CN '%s' is present in candidate SANs", oldCn));
        }

        Set<String> oldSans = DomainNameNormalizer.parseAndNormalizeSans(
                oldCertificate.getSubjectAlternativeNames(), oldCertificate.getCommonName());
        if (oldSans.contains(newCn)) {
            return MatchReason.of(getSignal(), 0.70, getWeight(),
                    String.format("Candidate CN '%s' was present in existing SANs", newCn));
        }

        // 4. Mismatch
        return MatchReason.of(getSignal(), 0.0, getWeight(),
                String.format("Common Name mismatch: '%s' vs '%s'", oldCn, newCn));
    }
}
