package com.grassroots.cdm.matching.evaluator.impl;

import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.matching.config.MatchingProperties;
import com.grassroots.cdm.matching.evaluator.MatchSignalEvaluator;
import com.grassroots.cdm.matching.model.MatchReason;
import com.grassroots.cdm.matching.model.MatchSignal;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Evaluates Certificate Authority (CA) continuity and issuer organization alignment.
 */
@Component
public class IssuerContinuityEvaluator implements MatchSignalEvaluator {

    private final MatchingProperties properties;

    public IssuerContinuityEvaluator(MatchingProperties properties) {
        this.properties = properties;
    }

    @Override
    public MatchSignal getSignal() {
        return MatchSignal.ISSUER_CONTINUITY;
    }

    @Override
    public double getWeight() {
        return properties.getIssuerWeight();
    }

    @Override
    public MatchReason evaluate(CertificateRecord oldCertificate, CertificateRecord candidate) {
        String oldIssuer = oldCertificate.getIssuer();
        String newIssuer = candidate.getIssuer();

        if (oldIssuer == null || newIssuer == null || oldIssuer.isBlank() || newIssuer.isBlank()) {
            return MatchReason.of(getSignal(), 0.50, getWeight(), "Issuer metadata partially unspecified");
        }

        String normOld = oldIssuer.trim().toLowerCase(Locale.ROOT);
        String normNew = newIssuer.trim().toLowerCase(Locale.ROOT);

        // 1. Exact match
        if (normOld.equals(normNew)) {
            return MatchReason.of(getSignal(), 1.0, getWeight(), "Identical CA Issuer: " + oldIssuer);
        }

        // 2. Same CA brand/organization (e.g. both Sectigo or both DigiCert)
        if (isSameCaOrganization(normOld, normNew)) {
            return MatchReason.of(getSignal(), 0.80, getWeight(),
                    String.format("Same CA organization family: '%s' <-> '%s'", oldIssuer, newIssuer));
        }

        // 3. Different CA (allowed, but no continuity bonus)
        return MatchReason.of(getSignal(), 0.0, getWeight(),
                String.format("Different CA Issuers: '%s' vs '%s'", oldIssuer, newIssuer));
    }

    private boolean isSameCaOrganization(String i1, String i2) {
        String[] knownCas = {"sectigo", "digicert", "lets encrypt", "entrust", "globalsign", "geotrust", "thawte", "comodo"};
        for (String ca : knownCas) {
            if (i1.contains(ca) && i2.contains(ca)) {
                return true;
            }
        }
        return false;
    }
}
