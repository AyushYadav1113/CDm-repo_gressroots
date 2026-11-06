package com.grassroots.cdm.service;

import com.grassroots.cdm.dto.CertificateSummaryDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for querying certificate inventory records.
 */
public interface CertificateService {

    /**
     * Retrieves paginated certificate summary records matching optional filter criteria.
     */
    PagedResponse<CertificateSummaryDto> getCertificates(
            CertificateStatus status,
            CertificateSource source,
            String commonName,
            String issuer,
            Pageable pageable
    );

    /**
     * Retrieves a single certificate by its unique UUID identifier.
     *
     * @param id certificate UUID
     * @return certificate summary DTO
     * @throws com.grassroots.cdm.exception.ResourceNotFoundException if no certificate exists with the ID
     */
    CertificateSummaryDto getCertificateById(UUID id);
}
