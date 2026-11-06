package com.grassroots.cdm.service.impl;

import com.grassroots.cdm.dto.CdmDtoMapper;
import com.grassroots.cdm.dto.MidServerDto;
import com.grassroots.cdm.dto.MidServerHealthDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.repository.MidServerRepository;
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
class MidServerServiceImplTest {

    @Mock
    private MidServerRepository midServerRepository;

    @Spy
    private CdmDtoMapper dtoMapper = new CdmDtoMapper();

    private MidServerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MidServerServiceImpl(midServerRepository, dtoMapper);
    }

    @Test
    @DisplayName("getMidServers returns paged results mapped to DTOs")
    void getMidServers_returnsPagedResults() {
        MidServer mid = new MidServer("mid-node-01", "https://mid:8443", MidServerStatus.UP);
        mid.setId(UUID.randomUUID());

        PageImpl<MidServer> page = new PageImpl<>(List.of(mid), PageRequest.of(0, 20), 1);
        when(midServerRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PagedResponse<MidServerDto> response = service.getMidServers(MidServerStatus.UP, PageRequest.of(0, 20));

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).name()).isEqualTo("mid-node-01");
    }

    @Test
    @DisplayName("getMidServerById returns DTO when node exists")
    void getMidServerById_found_returnsDto() {
        UUID midId = UUID.randomUUID();
        MidServer mid = new MidServer("mid-node-02", "https://mid2:8443", MidServerStatus.UP);
        mid.setId(midId);

        when(midServerRepository.findById(midId)).thenReturn(Optional.of(mid));

        MidServerDto dto = service.getMidServerById(midId);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(midId);
        assertThat(dto.name()).isEqualTo("mid-node-02");
    }

    @Test
    @DisplayName("getMidServerById throws ResourceNotFoundException when missing")
    void getMidServerById_notFound_throwsException() {
        UUID midId = UUID.randomUUID();
        when(midServerRepository.findById(midId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMidServerById(midId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("MID Server not found with ID: " + midId);
    }

    @Test
    @DisplayName("getMidServerHealth returns diagnostic health DTO")
    void getMidServerHealth_returnsHealthDto() {
        UUID midId = UUID.randomUUID();
        MidServer mid = new MidServer("mid-node-03", "https://mid3:8443", MidServerStatus.UP);
        mid.setId(midId);
        mid.setHealthInfo("{\"cpu\":15,\"memory\":30}");

        when(midServerRepository.findById(midId)).thenReturn(Optional.of(mid));

        MidServerHealthDto healthDto = service.getMidServerHealth(midId);

        assertThat(healthDto).isNotNull();
        assertThat(healthDto.id()).isEqualTo(midId);
        assertThat(healthDto.healthy()).isTrue();
        assertThat(healthDto.healthInfo()).isEqualTo("{\"cpu\":15,\"memory\":30}");
    }
}
