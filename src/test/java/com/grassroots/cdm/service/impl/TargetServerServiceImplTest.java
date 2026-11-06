package com.grassroots.cdm.service.impl;

import com.grassroots.cdm.dto.CdmDtoMapper;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.dto.TargetServerDto;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.repository.TargetServerRepository;
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
class TargetServerServiceImplTest {

    @Mock
    private TargetServerRepository serverRepository;

    @Spy
    private CdmDtoMapper dtoMapper = new CdmDtoMapper();

    private TargetServerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TargetServerServiceImpl(serverRepository, dtoMapper);
    }

    @Test
    @DisplayName("getServers returns paged results mapped to DTOs")
    void getServers_returnsPagedResults() {
        TargetServer server = new TargetServer("node-01.corp", "10.0.0.5", ServerOperatingSystem.LINUX_RHEL, ServerTechnology.NGINX, EnvironmentType.PRODUCTION);
        server.setId(UUID.randomUUID());

        PageImpl<TargetServer> page = new PageImpl<>(List.of(server), PageRequest.of(0, 20), 1);
        when(serverRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PagedResponse<TargetServerDto> response = service.getServers(
                EnvironmentType.PRODUCTION, ServerTechnology.NGINX, ServerStatus.ACTIVE, "node-01", PageRequest.of(0, 20));

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).hostname()).isEqualTo("node-01.corp");
    }

    @Test
    @DisplayName("getServerById returns DTO when server exists")
    void getServerById_found_returnsDto() {
        UUID serverId = UUID.randomUUID();
        TargetServer server = new TargetServer("node-02.corp", "10.0.0.6", ServerOperatingSystem.LINUX_RHEL, ServerTechnology.APACHE, EnvironmentType.PRODUCTION);
        server.setId(serverId);

        when(serverRepository.findById(serverId)).thenReturn(Optional.of(server));

        TargetServerDto dto = service.getServerById(serverId);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(serverId);
        assertThat(dto.hostname()).isEqualTo("node-02.corp");
    }

    @Test
    @DisplayName("getServerById throws ResourceNotFoundException when server is missing")
    void getServerById_notFound_throwsException() {
        UUID serverId = UUID.randomUUID();
        when(serverRepository.findById(serverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getServerById(serverId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Target server not found with ID: " + serverId);
    }
}
