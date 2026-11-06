package com.grassroots.cdm.controller;

import com.grassroots.cdm.configuration.CdmSecurityProperties;
import com.grassroots.cdm.configuration.SecurityConfig;
import com.grassroots.cdm.dto.CertificateReplacementDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.security.CustomAccessDeniedHandler;
import com.grassroots.cdm.security.CustomAuthenticationEntryPoint;
import com.grassroots.cdm.service.CertificateReplacementService;
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

@WebMvcTest(ReplacementController.class)
@Import({SecurityConfig.class, CdmSecurityProperties.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class ReplacementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CertificateReplacementService replacementService;

    @Test
    @DisplayName("GET /api/v1/replacements without authentication returns 401 Unauthorized")
    void getReplacements_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/replacements")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/replacements returns paginated replacement correlations")
    void getReplacements_authenticated_returns200() throws Exception {
        UUID repId = UUID.randomUUID();
        CertificateReplacementDto dto = new CertificateReplacementDto(
                repId,
                UUID.randomUUID(),
                "old.grassroots.org",
                "OLDTHUMBPRINT123",
                UUID.randomUUID(),
                "new.grassroots.org",
                "NEWTHUMBPRINT456",
                1.0,
                "[\"CN_EXACT_MATCH\", \"SAN_SUBSET_MATCH\"]",
                "AUTO_MATCHED",
                Instant.now()
        );

        PagedResponse<CertificateReplacementDto> paged = new PagedResponse<>(
                List.of(dto), 0, 20, 1L, 1, true, true, false, false);

        when(replacementService.getReplacements(any(), any(), any(), any(Pageable.class)))
                .thenReturn(paged);

        mockMvc.perform(get("/api/v1/replacements?page=0&size=20&matchStatus=AUTO_MATCHED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(repId.toString()))
                .andExpect(jsonPath("$.data.content[0].oldCommonName").value("old.grassroots.org"))
                .andExpect(jsonPath("$.data.content[0].newCommonName").value("new.grassroots.org"))
                .andExpect(jsonPath("$.data.content[0].matchStatus").value("AUTO_MATCHED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/replacements/{id} with existing ID returns 200")
    void getReplacementById_existingId_returns200() throws Exception {
        UUID repId = UUID.randomUUID();
        CertificateReplacementDto dto = new CertificateReplacementDto(
                repId,
                UUID.randomUUID(),
                "old.grassroots.org",
                "OLDTHUMBPRINT123",
                UUID.randomUUID(),
                "new.grassroots.org",
                "NEWTHUMBPRINT456",
                0.95,
                "[\"CN_EXACT_MATCH\"]",
                "MANUAL_MATCHED",
                Instant.now()
        );

        when(replacementService.getReplacementById(repId)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/replacements/" + repId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(repId.toString()))
                .andExpect(jsonPath("$.data.matchingScore").value(0.95));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/replacements/{id} with non-existing ID returns 404")
    void getReplacementById_notFound_returns404() throws Exception {
        UUID repId = UUID.randomUUID();
        when(replacementService.getReplacementById(repId))
                .thenThrow(new ResourceNotFoundException("Certificate replacement not found with ID: " + repId));

        mockMvc.perform(get("/api/v1/replacements/" + repId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
