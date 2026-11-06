package com.grassroots.cdm.service.impl;

import com.grassroots.cdm.dto.CdmDtoMapper;
import com.grassroots.cdm.dto.CertificateReplacementDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.CertificateReplacement;
import com.grassroots.cdm.entity.enums.MatchStatus;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.repository.CertificateReplacementRepository;
import com.grassroots.cdm.service.CertificateReplacementService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CertificateReplacementServiceImpl implements CertificateReplacementService {

    private static final Logger log = LoggerFactory.getLogger(CertificateReplacementServiceImpl.class);

    private final CertificateReplacementRepository replacementRepository;
    private final CdmDtoMapper dtoMapper;

    public CertificateReplacementServiceImpl(CertificateReplacementRepository replacementRepository, CdmDtoMapper dtoMapper) {
        this.replacementRepository = replacementRepository;
        this.dtoMapper = dtoMapper;
    }

    @Override
    public PagedResponse<CertificateReplacementDto> getReplacements(
            MatchStatus matchStatus,
            UUID oldCertificateId,
            UUID newCertificateId,
            Pageable pageable
    ) {
        log.debug("Fetching certificate replacements: matchStatus={}, oldCert={}, newCert={}, pageable={}",
                matchStatus, oldCertificateId, newCertificateId, pageable);

        Specification<CertificateReplacement> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (matchStatus != null) {
                predicates.add(cb.equal(root.get("matchStatus"), matchStatus));
            }
            if (oldCertificateId != null) {
                predicates.add(cb.equal(root.get("oldCertificate").get("id"), oldCertificateId));
            }
            if (newCertificateId != null) {
                predicates.add(cb.equal(root.get("newCertificate").get("id"), newCertificateId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<CertificateReplacement> page = replacementRepository.findAll(spec, pageable);
        return PagedResponse.from(page, dtoMapper::toCertificateReplacementDto);
    }

    @Override
    public CertificateReplacementDto getReplacementById(UUID id) {
        log.debug("Fetching certificate replacement by ID: {}", id);
        if (id == null) {
            throw new IllegalArgumentException("Replacement ID must not be null");
        }
        return replacementRepository.findById(id)
                .map(dtoMapper::toCertificateReplacementDto)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate replacement not found with ID: " + id));
    }
}
