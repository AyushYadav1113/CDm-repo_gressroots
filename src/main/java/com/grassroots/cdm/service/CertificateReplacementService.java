package com.grassroots.cdm.service;

import com.grassroots.cdm.dto.CertificateReplacementDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.enums.MatchStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for querying old-to-new certificate replacement correlations.
 */
public interface CertificateReplacementService {

    /**
     * Retrieves paginated certificate replacement records matching optional filter criteria.
     */
    PagedResponse<CertificateReplacementDto> getReplacements(
            MatchStatus matchStatus,
            UUID oldCertificateId,
            UUID newCertificateId,
            Pageable pageable
    );

    /**
     * Retrieves a single replacement correlation by its unique UUID identifier.
     *
     * @param id replacement UUID
     * @return certificate replacement DTO
     * @throws com.grassroots.cdm.exception.ResourceNotFoundException if no replacement exists with the ID
     */
    CertificateReplacementDto getReplacementById(UUID id);
}
