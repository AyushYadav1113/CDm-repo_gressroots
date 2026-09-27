package com.grassroots.cdm.matching.generator.impl;

import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.matching.config.MatchingProperties;
import com.grassroots.cdm.matching.generator.CandidateGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Production implementation of {@link CandidateGenerator}.
 * Pre-screens candidate pools, removes self-matches, and deduplicates candidates deterministically.
 */
@Component
public class DefaultCandidateGenerator implements CandidateGenerator {

    private static final Logger log = LoggerFactory.getLogger(DefaultCandidateGenerator.class);

    private final MatchingProperties properties;

    public DefaultCandidateGenerator(MatchingProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<CertificateRecord> generateCandidates(CertificateRecord oldCertificate, List<CertificateRecord> availablePool) {
        if (oldCertificate == null || availablePool == null || availablePool.isEmpty()) {
            return List.of();
        }

        // Deduplicate candidates preserving deterministic insertion order
        Map<String, CertificateRecord> uniqueCandidates = new LinkedHashMap<>();

        for (CertificateRecord candidate : availablePool) {
            if (candidate == null) {
                continue;
            }

            // Exclude self-matching
            if (isSameCertificate(oldCertificate, candidate)) {
                log.debug("Excluding self-match candidate id={}, thumbprint={}", candidate.getId(), candidate.getThumbprint());
                continue;
            }

            String key = computeDeduplicationKey(candidate);
            uniqueCandidates.putIfAbsent(key, candidate);
        }

        return new ArrayList<>(uniqueCandidates.values());
    }

    private boolean isSameCertificate(CertificateRecord a, CertificateRecord b) {
        if (a.getId() != null && b.getId() != null && a.getId().equals(b.getId())) {
            return true;
        }
        if (a.getThumbprint() != null && b.getThumbprint() != null
                && a.getThumbprint().equalsIgnoreCase(b.getThumbprint())) {
            return true;
        }
        if (a.getSerialNumber() != null && b.getSerialNumber() != null
                && a.getSerialNumber().equalsIgnoreCase(b.getSerialNumber())
                && Objects.equals(a.getIssuer(), b.getIssuer())) {
            return true;
        }
        return false;
    }

    private String computeDeduplicationKey(CertificateRecord cert) {
        if (cert.getThumbprint() != null && !cert.getThumbprint().isBlank()) {
            return "THUMB:" + cert.getThumbprint().toUpperCase();
        }
        if (cert.getId() != null) {
            return "ID:" + cert.getId();
        }
        if (cert.getExternalId() != null && !cert.getExternalId().isBlank()) {
            return "EXT:" + cert.getExternalId();
        }
        return "SERIAL:" + (cert.getSerialNumber() != null ? cert.getSerialNumber() : "") + ":" +
                (cert.getCommonName() != null ? cert.getCommonName() : "");
    }
}
