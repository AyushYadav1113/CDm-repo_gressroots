package com.grassroots.cdm.service.impl;

import com.grassroots.cdm.dto.CdmDtoMapper;
import com.grassroots.cdm.dto.CertificateReplacementDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.CertificateReplacement;
import com.grassroots.cdm.entity.enums.MatchStatus;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.repository.CertificateReplacementRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateReplacementServiceImplTest {

    @Mock
    private CertificateReplacementRepository replacementRepository;

    @Spy
    private CdmDtoMapper dtoMapper = new CdmDtoMapper();

    private CertificateReplacementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CertificateReplacementServiceImpl(replacementRepository, dtoMapper);
    }

    @Test
    @DisplayName("getReplacements returns paged results mapped to DTOs")
    void getReplacements_returnsPagedResults() {
        CertificateRecord oldCert = new CertificateRecord();
        oldCert.setId(UUID.randomUUID());
        oldCert.setCommonName("old.corp");

        CertificateRecord newCert = new CertificateRecord();
        newCert.setId(UUID.randomUUID());
        newCert.setCommonName("new.corp");

        CertificateReplacement rep = new CertificateReplacement(oldCert, newCert, 1.0, "[\"CN_EXACT\"]", MatchStatus.AUTO_MATCHED);
        rep.setId(UUID.randomUUID());

        PageImpl<CertificateReplacement> page = new PageImpl<>(List.of(rep), PageRequest.of(0, 20), 1);
        when(replacementRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PagedResponse<CertificateReplacementDto> response = service.getReplacements(
                MatchStatus.AUTO_MATCHED, oldCert.getId(), newCert.getId(), PageRequest.of(0, 20));

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).oldCommonName()).isEqualTo("old.corp");
    }

    @Test
    @DisplayName("getReplacementById returns DTO when replacement exists")
    void getReplacementById_found_returnsDto() {
        UUID repId = UUID.randomUUID();
        CertificateRecord oldCert = new CertificateRecord();
        oldCert.setId(UUID.randomUUID());
        oldCert.setCommonName("old.corp");

        CertificateRecord newCert = new CertificateRecord();
        newCert.setId(UUID.randomUUID());
        newCert.setCommonName("new.corp");

        CertificateReplacement rep = new CertificateReplacement(oldCert, newCert, 0.9, "[\"CN_MATCH\"]", MatchStatus.MANUALLY_CONFIRMED);
        rep.setId(repId);

        when(replacementRepository.findById(repId)).thenReturn(Optional.of(rep));

        CertificateReplacementDto dto = service.getReplacementById(repId);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(repId);
    }

    @Test
    @DisplayName("getReplacementById throws ResourceNotFoundException when missing")
    void getReplacementById_notFound_throwsException() {
        UUID repId = UUID.randomUUID();
        when(replacementRepository.findById(repId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReplacementById(repId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Certificate replacement not found with ID: " + repId);
    }
}
