package com.grassroots.cdm.controller;

import com.grassroots.cdm.configuration.CdmSecurityProperties;
import com.grassroots.cdm.configuration.SecurityConfig;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.dto.TargetServerDto;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.security.CustomAccessDeniedHandler;
import com.grassroots.cdm.security.CustomAuthenticationEntryPoint;
import com.grassroots.cdm.service.TargetServerService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ServerController.class)
@Import({SecurityConfig.class, CdmSecurityProperties.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class ServerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TargetServerService targetServerService;

    @Test
    @DisplayName("GET /api/v1/servers without authentication returns 401 Unauthorized")
    void getServers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/servers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/servers returns paginated target server list")
    void getServers_authenticated_returns200() throws Exception {
        UUID serverId = UUID.randomUUID();
        TargetServerDto serverDto = new TargetServerDto(
                serverId,
                "web-prod-01.grassroots.org",
                "10.0.1.15",
                "LINUX",
                "NGINX",
                "PRODUCTION",
                UUID.randomUUID(),
                "mid-node-01",
                "ACTIVE"
        );

        PagedResponse<TargetServerDto> paged = new PagedResponse<>(
                List.of(serverDto), 0, 20, 1L, 1, true, true, false, false);

        when(targetServerService.getServers(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(paged);

        mockMvc.perform(get("/api/v1/servers?page=0&size=20&environment=PRODUCTION")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(serverId.toString()))
                .andExpect(jsonPath("$.data.content[0].hostname").value("web-prod-01.grassroots.org"))
                .andExpect(jsonPath("$.data.content[0].technology").value("NGINX"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/servers/{id} with existing ID returns 200")
    void getServerById_existingId_returns200() throws Exception {
        UUID serverId = UUID.randomUUID();
        TargetServerDto serverDto = new TargetServerDto(
                serverId,
                "app-node-02.grassroots.org",
                "10.0.2.20",
                "LINUX",
                "APACHE_HTTPD",
                "PRODUCTION",
                null,
                null,
                "ACTIVE"
        );

        when(targetServerService.getServerById(serverId)).thenReturn(serverDto);

        mockMvc.perform(get("/api/v1/servers/" + serverId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(serverId.toString()))
                .andExpect(jsonPath("$.data.hostname").value("app-node-02.grassroots.org"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/servers/{id} with non-existing ID returns 404")
    void getServerById_notFound_returns404() throws Exception {
        UUID serverId = UUID.randomUUID();
        when(targetServerService.getServerById(serverId))
                .thenThrow(new ResourceNotFoundException("Target server not found with ID: " + serverId));

        mockMvc.perform(get("/api/v1/servers/" + serverId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
