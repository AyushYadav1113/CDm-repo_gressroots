package com.grassroots.cdm.service.impl;

import com.grassroots.cdm.dto.CdmDtoMapper;
import com.grassroots.cdm.dto.CertificateSummaryDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.repository.CertificateRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceImplTest {

    @Mock
    private CertificateRecordRepository certificateRepository;

    @Spy
    private CdmDtoMapper dtoMapper = new CdmDtoMapper();

    private CertificateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CertificateServiceImpl(certificateRepository, dtoMapper);
    }

    @Test
    @DisplayName("getCertificates returns paged results mapped to DTOs")
    void getCertificates_returnsPagedResults() {
        CertificateRecord cert = new CertificateRecord();
        cert.setId(UUID.randomUUID());
        cert.setCommonName("api.corp.grassroots.org");
        cert.setThumbprint("AABB112233");
        cert.setStatus(CertificateStatus.ACTIVE);
        cert.setSource(CertificateSource.SERVICENOW);
        cert.setValidFrom(Instant.now());
        cert.setValidTo(Instant.now().plusSeconds(86400));

        PageImpl<CertificateRecord> page = new PageImpl<>(List.of(cert), PageRequest.of(0, 20), 1);
        when(certificateRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PagedResponse<CertificateSummaryDto> response = service.getCertificates(
                CertificateStatus.ACTIVE, CertificateSource.SERVICENOW, "api.corp", "DigiCert", PageRequest.of(0, 20));

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).commonName()).isEqualTo("api.corp.grassroots.org");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getCertificateById returns DTO when certificate exists")
    void getCertificateById_found_returnsDto() {
        UUID certId = UUID.randomUUID();
        CertificateRecord cert = new CertificateRecord();
        cert.setId(certId);
        cert.setCommonName("vault.corp.grassroots.org");
        cert.setThumbprint("CCDDEE4455");

        when(certificateRepository.findById(certId)).thenReturn(Optional.of(cert));

        CertificateSummaryDto dto = service.getCertificateById(certId);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(certId);
        assertThat(dto.commonName()).isEqualTo("vault.corp.grassroots.org");
    }

    @Test
    @DisplayName("getCertificateById throws ResourceNotFoundException when certificate is missing")
    void getCertificateById_notFound_throwsException() {
        UUID certId = UUID.randomUUID();
        when(certificateRepository.findById(certId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCertificateById(certId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Certificate not found with ID: " + certId);
    }

    @Test
    @DisplayName("getCertificateById throws IllegalArgumentException when ID is null")
    void getCertificateById_nullId_throwsException() {
        assertThatThrownBy(() -> service.getCertificateById(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
