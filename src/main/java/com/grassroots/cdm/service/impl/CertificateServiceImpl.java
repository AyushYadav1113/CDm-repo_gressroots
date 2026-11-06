package com.grassroots.cdm.service.impl;

import com.grassroots.cdm.dto.CdmDtoMapper;
import com.grassroots.cdm.dto.CertificateSummaryDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.repository.CertificateRecordRepository;
import com.grassroots.cdm.service.CertificateService;
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
public class CertificateServiceImpl implements CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateServiceImpl.class);

    private final CertificateRecordRepository certificateRepository;
    private final CdmDtoMapper dtoMapper;

    public CertificateServiceImpl(CertificateRecordRepository certificateRepository, CdmDtoMapper dtoMapper) {
        this.certificateRepository = certificateRepository;
        this.dtoMapper = dtoMapper;
    }

    @Override
    public PagedResponse<CertificateSummaryDto> getCertificates(
            CertificateStatus status,
            CertificateSource source,
            String commonName,
            String issuer,
            Pageable pageable
    ) {
        log.debug("Fetching certificates: status={}, source={}, commonName={}, issuer={}, pageable={}",
                status, source, commonName, issuer, pageable);

        Specification<CertificateRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (source != null) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (commonName != null && !commonName.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("commonName")), "%" + commonName.toLowerCase().trim() + "%"));
            }
            if (issuer != null && !issuer.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("issuer")), "%" + issuer.toLowerCase().trim() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<CertificateRecord> page = certificateRepository.findAll(spec, pageable);
        return PagedResponse.from(page, dtoMapper::toCertificateSummaryDto);
    }

    @Override
    public CertificateSummaryDto getCertificateById(UUID id) {
        log.debug("Fetching certificate by ID: {}", id);
        if (id == null) {
            throw new IllegalArgumentException("Certificate ID must not be null");
        }
        return certificateRepository.findById(id)
                .map(dtoMapper::toCertificateSummaryDto)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found with ID: " + id));
    }
}
