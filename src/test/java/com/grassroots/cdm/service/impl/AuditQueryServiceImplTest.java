package com.grassroots.cdm.service.impl;

import com.grassroots.cdm.dto.AuditLogDto;
import com.grassroots.cdm.dto.CdmDtoMapper;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.AuditLogRecord;
import com.grassroots.cdm.repository.AuditLogRecordRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceImplTest {

    @Mock
    private AuditLogRecordRepository auditRepository;

    @Spy
    private CdmDtoMapper dtoMapper = new CdmDtoMapper();

    private AuditQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditQueryServiceImpl(auditRepository, dtoMapper);
    }

    @Test
    @DisplayName("getAuditEvents returns paged audit log results mapped to DTOs")
    void getAuditEvents_returnsPagedResults() {
        AuditLogRecord record = new AuditLogRecord("c1", "r1", "DEPLOYMENT_DISPATCHED", "Dispatched to MID", "Job", "j1", "admin", "SUCCESS", "{}", "127.0.0.1");
        record.setId(UUID.randomUUID());

        PageImpl<AuditLogRecord> page = new PageImpl<>(List.of(record), PageRequest.of(0, 50), 1);
        when(auditRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PagedResponse<AuditLogDto> response = service.getAuditEvents(
                "c1", "DEPLOYMENT_DISPATCHED", "DEPLOYMENT_DISPATCHED", "admin", "Job", "j1", "SUCCESS",
                Instant.now().minusSeconds(100), Instant.now(), PageRequest.of(0, 50));

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).correlationId()).isEqualTo("c1");
        assertThat(response.content().get(0).eventType()).isEqualTo("DEPLOYMENT_DISPATCHED");
    }
}
