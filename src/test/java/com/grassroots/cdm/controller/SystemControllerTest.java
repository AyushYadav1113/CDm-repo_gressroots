package com.grassroots.cdm.controller;

import com.grassroots.cdm.configuration.CdmSecurityProperties;
import com.grassroots.cdm.configuration.SecurityConfig;
import com.grassroots.cdm.dto.SystemStatusDto;
import com.grassroots.cdm.security.CustomAccessDeniedHandler;
import com.grassroots.cdm.security.CustomAuthenticationEntryPoint;
import com.grassroots.cdm.service.SystemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@Import({SecurityConfig.class, CdmSecurityProperties.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemService systemService;

    @Test
    @DisplayName("GET /api/v1/system/status succeeds publicly and returns structured JSON")
    void getStatus_ReturnsSystemStatus() throws Exception {
        SystemStatusDto mockStatus = new SystemStatusDto(
                "cdm-core",
                "1.0.0",
                "HEALTHY",
                Instant.now(),
                "21.0.0",
                Map.of("database", "UP (PostgreSQL 16.2)")
        );
        when(systemService.getSystemStatus()).thenReturn(mockStatus);

        mockMvc.perform(get("/api/v1/system/status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationName").value("cdm-core"))
                .andExpect(jsonPath("$.data.status").value("HEALTHY"))
                .andExpect(jsonPath("$.data.components.database").value("UP (PostgreSQL 16.2)"));
    }

    @Test
    @DisplayName("GET /api/v1/system/ping returns pong message")
    void ping_ReturnsPong() throws Exception {
        when(systemService.ping()).thenReturn("pong");

        mockMvc.perform(get("/api/v1/system/ping")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("pong"));
    }
}
