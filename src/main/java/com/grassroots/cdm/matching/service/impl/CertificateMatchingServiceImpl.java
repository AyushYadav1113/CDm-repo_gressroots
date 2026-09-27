package com.grassroots.cdm.matching.service.impl;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.CertificateReplacement;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.matching.CertificateMatcher;
import com.grassroots.cdm.matching.model.CandidateMatchResult;
import com.grassroots.cdm.matching.model.MatchDecision;
import com.grassroots.cdm.matching.service.CertificateMatchingService;
import com.grassroots.cdm.repository.CertificateRecordRepository;
import com.grassroots.cdm.repository.CertificateReplacementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link CertificateMatchingService}.
 * Orchestrates candidate evaluation, manages persistent CertificateReplacement correlations,
 * and maintains immutable compliance audit records.
 */
@Service
public class CertificateMatchingServiceImpl implements CertificateMatchingService {

    private static final Logger log = LoggerFactory.getLogger(CertificateMatchingServiceImpl.class);

    private final CertificateMatcher certificateMatcher;
    private final CertificateRecordRepository certificateRecordRepository;
    private final CertificateReplacementRepository certificateReplacementRepository;
    private final AuditService auditService;

    public CertificateMatchingServiceImpl(
            CertificateMatcher certificateMatcher,
            CertificateRecordRepository certificateRecordRepository,
            CertificateReplacementRepository certificateReplacementRepository,
            AuditService auditService
    ) {
        this.certificateMatcher = certificateMatcher;
        this.certificateRecordRepository = certificateRecordRepository;
        this.certificateReplacementRepository = certificateReplacementRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public CandidateMatchResult matchCertificate(UUID oldCertificateId, String correlationId) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        MDC.put("correlationId", resolvedCorrelationId);

        CertificateRecord oldCertificate = certificateRecordRepository.findById(oldCertificateId)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found for ID: " + oldCertificateId));

        log.info("Matching certificate cn={}, id={}, serial={}, correlationId={}",
                oldCertificate.getCommonName(), oldCertificate.getId(), oldCertificate.getSerialNumber(), resolvedCorrelationId);

        // Fetch candidate pool from repository
        List<CertificateRecord> allCandidates = certificateRecordRepository.findAll();

        CandidateMatchResult result = certificateMatcher.match(oldCertificate, allCandidates);

        // Persist CertificateReplacement if match decision is AUTOMATIC_MATCH or REVIEW_REQUIRED
        if (result.decision() != MatchDecision.NO_MATCH && result.matchedCandidate().isPresent()) {
            CertificateRecord newCertificate = result.matchedCandidate().get();
            persistReplacement(oldCertificate, newCertificate, result);
        }

        return result;
    }

    @Override
    @Transactional
    public List<CandidateMatchResult> matchAllExpiringCertificates(String correlationId) {
        String resolvedCorrelationId = resolveCorrelationId(correlationId);
        MDC.put("correlationId", resolvedCorrelationId);

        List<CertificateRecord> expiringCertificates = certificateRecordRepository.findByStatus(CertificateStatus.EXPIRING);
        List<CertificateRecord> allCandidates = certificateRecordRepository.findAll();
        List<CandidateMatchResult> results = new ArrayList<>();

        log.info("Running batch matching across {} expiring certificates (correlationId={})",
                expiringCertificates.size(), resolvedCorrelationId);

        for (CertificateRecord oldCert : expiringCertificates) {
            try {
                CandidateMatchResult result = certificateMatcher.match(oldCert, allCandidates);
                if (result.decision() != MatchDecision.NO_MATCH && result.matchedCandidate().isPresent()) {
                    persistReplacement(oldCert, result.matchedCandidate().get(), result);
                }
                results.add(result);
            } catch (Exception ex) {
                log.error("Failed matching expiring certificate id={}, cn={}: {}",
                        oldCert.getId(), oldCert.getCommonName(), ex.getMessage(), ex);
            }
        }

        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CertificateReplacement> getReplacementForOldCertificate(UUID oldCertificateId) {
        List<CertificateReplacement> replacements = certificateReplacementRepository.findByOldCertificateId(oldCertificateId);
        return replacements.isEmpty() ? Optional.empty() : Optional.of(replacements.get(0));
    }

    private void persistReplacement(CertificateRecord oldCert, CertificateRecord newCert, CandidateMatchResult result) {
        List<CertificateReplacement> existingList = certificateReplacementRepository.findByOldCertificateId(oldCert.getId());
        CertificateReplacement replacement;

        if (!existingList.isEmpty()) {
            replacement = existingList.get(0);
            replacement.setNewCertificate(newCert);
            replacement.setMatchingScore(result.matchScore());
            replacement.setMatchingReasons(result.reasonsJson());
            replacement.setMatchStatus(result.decision().toMatchStatus());
        } else {
            replacement = new CertificateReplacement(
                    oldCert,
                    newCert,
                    result.matchScore(),
                    result.reasonsJson(),
                    result.decision().toMatchStatus()
            );
        }

        CertificateReplacement saved = certificateReplacementRepository.saveAndFlush(replacement);
        log.info("Saved CertificateReplacement record id={}, oldCert={}, newCert={}, score={:.4f}, status={}",
                saved.getId(), oldCert.getCommonName(), newCert.getCommonName(), result.matchScore(), result.decision().toMatchStatus());

        auditService.recordAudit(AuditEvent.create(
                AuditAction.CERTIFICATE_MATCHED,
                "CertificateReplacement",
                saved.getId().toString(),
                "SYSTEM",
                result.decision().name(),
                result.reasonsJson(),
                "127.0.0.1"
        ));
    }

    private String resolveCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        String mdc = MDC.get("correlationId");
        if (mdc != null && !mdc.isBlank()) {
            return mdc;
        }
        return UUID.randomUUID().toString();
    }
}
