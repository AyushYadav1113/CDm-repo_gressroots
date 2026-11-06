package com.grassroots.cdm.controller;

import com.grassroots.cdm.configuration.CdmSecurityProperties;
import com.grassroots.cdm.configuration.SecurityConfig;
import com.grassroots.cdm.dto.AuditLogDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.security.CustomAccessDeniedHandler;
import com.grassroots.cdm.security.CustomAuthenticationEntryPoint;
import com.grassroots.cdm.service.AuditQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditController.class)
@Import({SecurityConfig.class, CdmSecurityProperties.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditQueryService auditQueryService;

    @Test
    @DisplayName("GET /api/v1/audit-events without authentication returns 401 Unauthorized")
    void getAuditEvents_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/audit-events returns paginated audit log events")
    void getAuditEvents_authenticated_returns200() throws Exception {
        UUID auditId = UUID.randomUUID();
        AuditLogDto dto = new AuditLogDto(
                auditId,
                "CORR-AUDIT-001",
                "REF-001",
                "DEPLOYMENT_EXECUTED",
                "Deployment successfully executed on target host",
                "DeploymentJob",
                UUID.randomUUID().toString(),
                "ORCHESTRATOR",
                "SUCCESS",
                "{\"targetHost\":\"app-01.corp.grassroots.org\"}",
                "127.0.0.1",
                Instant.now()
        );

        PagedResponse<AuditLogDto> paged = new PagedResponse<>(
                List.of(dto), 0, 50, 1L, 1, true, true, false, false);

        when(auditQueryService.getAuditEvents(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(paged);

        mockMvc.perform(get("/api/v1/audit-events?page=0&size=50&outcome=SUCCESS")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(auditId.toString()))
                .andExpect(jsonPath("$.data.content[0].correlationId").value("CORR-AUDIT-001"))
                .andExpect(jsonPath("$.data.content[0].eventType").value("DEPLOYMENT_EXECUTED"))
                .andExpect(jsonPath("$.data.content[0].outcome").value("SUCCESS"));
    }
}
