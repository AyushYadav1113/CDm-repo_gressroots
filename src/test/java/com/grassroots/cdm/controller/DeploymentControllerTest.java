package com.grassroots.cdm.controller;

import com.grassroots.cdm.configuration.CdmSecurityProperties;
import com.grassroots.cdm.configuration.SecurityConfig;
import com.grassroots.cdm.dto.DeploymentJobDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.security.CustomAccessDeniedHandler;
import com.grassroots.cdm.security.CustomAuthenticationEntryPoint;
import com.grassroots.cdm.service.DeploymentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeploymentController.class)
@Import({SecurityConfig.class, CdmSecurityProperties.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class DeploymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeploymentService deploymentService;

    private DeploymentJobDto createDto(UUID id, String status) {
        return new DeploymentJobDto(
                id,
                "JOB-" + id.toString().substring(0, 8),
                "IDEMPOTENT-" + id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "web-01.grassroots.org",
                443,
                "NGINX",
                "RENEWAL_REPLACEMENT",
                status,
                1,
                3,
                null,
                "MID-TASK-999",
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(1800),
                null
        );
    }

    @Test
    @DisplayName("GET /api/v1/deployments without authentication returns 401 Unauthorized")
    void getDeployments_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/deployments returns paginated deployment jobs")
    void getDeployments_authenticated_returns200() throws Exception {
        UUID jobId = UUID.randomUUID();
        DeploymentJobDto dto = createDto(jobId, "COMPLETED");

        PagedResponse<DeploymentJobDto> paged = new PagedResponse<>(
                List.of(dto), 0, 20, 1L, 1, true, true, false, false);

        when(deploymentService.getDeployments(any(), any(), any(), any(Pageable.class)))
                .thenReturn(paged);

        mockMvc.perform(get("/api/v1/deployments?page=0&size=20&status=COMPLETED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(jobId.toString()))
                .andExpect(jsonPath("$.data.content[0].targetHost").value("web-01.grassroots.org"))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/deployments/{id} with existing ID returns 200")
    void getDeploymentById_existingId_returns200() throws Exception {
        UUID jobId = UUID.randomUUID();
        DeploymentJobDto dto = createDto(jobId, "VERIFIED");

        when(deploymentService.getDeploymentById(jobId)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/deployments/" + jobId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(jobId.toString()))
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/deployments/{id} with non-existing ID returns 404")
    void getDeploymentById_notFound_returns404() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(deploymentService.getDeploymentById(jobId))
                .thenThrow(new ResourceNotFoundException("Deployment job not found with ID: " + jobId));

        mockMvc.perform(get("/api/v1/deployments/" + jobId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/deployments/{id}/retry triggers retry and returns 200")
    void retryDeployment_authenticated_returns200() throws Exception {
        UUID jobId = UUID.randomUUID();
        DeploymentJobDto dto = createDto(jobId, "RETRY_PENDING");

        when(deploymentService.retryDeployment(eq(jobId), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/deployments/" + jobId + "/retry")
                        .header("X-Correlation-ID", "corr-retry-01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(jobId.toString()))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Deployment retry triggered")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/deployments/{id}/verify triggers live verification and returns 200")
    void verifyDeployment_authenticated_returns200() throws Exception {
        UUID jobId = UUID.randomUUID();
        DeploymentJobDto dto = createDto(jobId, "VERIFIED");

        when(deploymentService.verifyDeployment(eq(jobId), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/deployments/" + jobId + "/verify")
                        .header("X-Correlation-ID", "corr-verify-01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(jobId.toString()))
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));
    }
}
