package com.grassroots.cdm.controller;

import com.grassroots.cdm.configuration.CdmSecurityProperties;
import com.grassroots.cdm.configuration.SecurityConfig;
import com.grassroots.cdm.dto.MidServerDto;
import com.grassroots.cdm.dto.MidServerHealthDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.security.CustomAccessDeniedHandler;
import com.grassroots.cdm.security.CustomAuthenticationEntryPoint;
import com.grassroots.cdm.service.MidServerService;
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

@WebMvcTest(MidServerController.class)
@Import({SecurityConfig.class, CdmSecurityProperties.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class MidServerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MidServerService midServerService;

    @Test
    @DisplayName("GET /api/v1/mid-servers without authentication returns 401 Unauthorized")
    void getMidServers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/mid-servers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/mid-servers returns paginated MID servers")
    void getMidServers_authenticated_returns200() throws Exception {
        UUID midId = UUID.randomUUID();
        MidServerDto dto = new MidServerDto(
                midId,
                "mid-prod-cluster-01",
                "https://mid01.internal.grassroots.org:8443",
                "UP",
                "{\"ip\":\"10.0.10.50\"}",
                "{\"cpuUsage\":12,\"memoryUsage\":45}"
        );

        PagedResponse<MidServerDto> paged = new PagedResponse<>(
                List.of(dto), 0, 20, 1L, 1, true, true, false, false);

        when(midServerService.getMidServers(any(), any(Pageable.class))).thenReturn(paged);

        mockMvc.perform(get("/api/v1/mid-servers?page=0&size=20&status=UP")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(midId.toString()))
                .andExpect(jsonPath("$.data.content[0].name").value("mid-prod-cluster-01"))
                .andExpect(jsonPath("$.data.content[0].status").value("UP"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/mid-servers/{id} with existing ID returns 200")
    void getMidServerById_existingId_returns200() throws Exception {
        UUID midId = UUID.randomUUID();
        MidServerDto dto = new MidServerDto(
                midId,
                "mid-prod-02",
                "https://mid02.internal.grassroots.org:8443",
                "UP",
                null,
                null
        );

        when(midServerService.getMidServerById(midId)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/mid-servers/" + midId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(midId.toString()))
                .andExpect(jsonPath("$.data.name").value("mid-prod-02"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/mid-servers/{id}/health returns health diagnostics")
    void getMidServerHealth_existingId_returns200() throws Exception {
        UUID midId = UUID.randomUUID();
        MidServerHealthDto healthDto = new MidServerHealthDto(
                midId,
                "mid-prod-02",
                "https://mid02.internal.grassroots.org:8443",
                "UP",
                true,
                "{\"uptimeHours\":480,\"pingLatencyMs\":12}",
                "{\"ip\":\"10.0.10.51\"}",
                Instant.now()
        );

        when(midServerService.getMidServerHealth(midId)).thenReturn(healthDto);

        mockMvc.perform(get("/api/v1/mid-servers/" + midId + "/health")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(midId.toString()))
                .andExpect(jsonPath("$.data.healthy").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/mid-servers/{id} with non-existing ID returns 404")
    void getMidServerById_notFound_returns404() throws Exception {
        UUID midId = UUID.randomUUID();
        when(midServerService.getMidServerById(midId))
                .thenThrow(new ResourceNotFoundException("MID Server not found with ID: " + midId));

        mockMvc.perform(get("/api/v1/mid-servers/" + midId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
