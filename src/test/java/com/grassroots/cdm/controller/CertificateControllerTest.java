package com.grassroots.cdm.controller;

import com.grassroots.cdm.configuration.CdmSecurityProperties;
import com.grassroots.cdm.configuration.SecurityConfig;
import com.grassroots.cdm.dto.CertificateSummaryDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.security.CustomAccessDeniedHandler;
import com.grassroots.cdm.security.CustomAuthenticationEntryPoint;
import com.grassroots.cdm.service.CertificateService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CertificateController.class)
@Import({SecurityConfig.class, CdmSecurityProperties.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class CertificateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CertificateService certificateService;

    @Test
    @DisplayName("GET /api/v1/certificates without authentication returns 401 Unauthorized")
    void getCertificates_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/certificates")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/certificates with ADMIN role returns paginated certificate list")
    void getCertificates_authenticated_returns200() throws Exception {
        UUID certId = UUID.randomUUID();
        CertificateSummaryDto certDto = new CertificateSummaryDto(
                certId,
                "EXT-100",
                "1234567890",
                "AABBCCDDEEFF00112233445566778899AABBCCDD",
                "app.grassroots.org",
                "DNS:app.grassroots.org, DNS:app.internal",
                "DigiCert Global CA",
                Instant.now().minusSeconds(86400),
                Instant.now().plusSeconds(86400 * 30),
                "SERVICENOW",
                "ACTIVE"
        );

        PagedResponse<CertificateSummaryDto> paged = new PagedResponse<>(
                List.of(certDto), 0, 20, 1L, 1, true, true, false, false);

        when(certificateService.getCertificates(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(paged);

        mockMvc.perform(get("/api/v1/certificates?page=0&size=20&status=ACTIVE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(certId.toString()))
                .andExpect(jsonPath("$.data.content[0].commonName").value("app.grassroots.org"))
                .andExpect(jsonPath("$.data.content[0].thumbprint").value("AABBCCDDEEFF00112233445566778899AABBCCDD"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    @WithMockUser(roles = "ORCHESTRATOR")
    @DisplayName("GET /api/v1/certificates/{id} with existing ID returns 200 and certificate details")
    void getCertificateById_existingId_returns200() throws Exception {
        UUID certId = UUID.randomUUID();
        CertificateSummaryDto certDto = new CertificateSummaryDto(
                certId,
                "EXT-200",
                "9876543210",
                "1122334455667788990011223344556677889900",
                "secure.grassroots.org",
                "DNS:secure.grassroots.org",
                "Sectigo RSA CA",
                Instant.now().minusSeconds(86400),
                Instant.now().plusSeconds(86400 * 90),
                "SECTIGO",
                "ACTIVE"
        );

        when(certificateService.getCertificateById(certId)).thenReturn(certDto);

        mockMvc.perform(get("/api/v1/certificates/" + certId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(certId.toString()))
                .andExpect(jsonPath("$.data.commonName").value("secure.grassroots.org"))
                .andExpect(jsonPath("$.data.source").value("SECTIGO"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/certificates/{id} with non-existing ID returns 404 Not Found")
    void getCertificateById_notFound_returns404() throws Exception {
        UUID certId = UUID.randomUUID();
        when(certificateService.getCertificateById(certId))
                .thenThrow(new ResourceNotFoundException("Certificate not found with ID: " + certId));

        mockMvc.perform(get("/api/v1/certificates/" + certId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Certificate not found")));
    }
}
