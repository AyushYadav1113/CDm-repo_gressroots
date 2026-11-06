package com.grassroots.cdm.service.impl;

import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.deployment.workflow.DeploymentWorkflowService;
import com.grassroots.cdm.dto.CdmDtoMapper;
import com.grassroots.cdm.dto.DeploymentJobDto;
import com.grassroots.cdm.dto.PagedResponse;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.enums.DeploymentType;
import com.grassroots.cdm.exception.ResourceNotFoundException;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import com.grassroots.cdm.workflow.WorkflowEngine;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentServiceImplTest {

    @Mock
    private DeploymentJobRepository deploymentJobRepository;

    @Mock
    private DeploymentWorkflowService workflowService;

    @Mock
    private WorkflowEngine workflowEngine;

    @Mock
    private AuditService auditService;

    @Spy
    private CdmDtoMapper dtoMapper = new CdmDtoMapper();

    private DeploymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeploymentServiceImpl(deploymentJobRepository, workflowService, workflowEngine, auditService, dtoMapper);
    }

    @Test
    @DisplayName("getDeployments returns paged results mapped to DTOs")
    void getDeployments_returnsPagedResults() {
        DeploymentJob job = new DeploymentJob();
        job.setId(UUID.randomUUID());
        job.setStatus(DeploymentJobStatus.VERIFIED);
        job.setTargetHost("app-01.corp");
        job.setDeploymentType(DeploymentType.RENEWAL_REPLACEMENT);

        PageImpl<DeploymentJob> page = new PageImpl<>(List.of(job), PageRequest.of(0, 20), 1);
        when(deploymentJobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PagedResponse<DeploymentJobDto> response = service.getDeployments(
                DeploymentJobStatus.VERIFIED, "app-01", DeploymentType.RENEWAL_REPLACEMENT, PageRequest.of(0, 20));

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).status()).isEqualTo("VERIFIED");
    }

    @Test
    @DisplayName("getDeploymentById returns DTO when job exists")
    void getDeploymentById_found_returnsDto() {
        UUID jobId = UUID.randomUUID();
        DeploymentJob job = new DeploymentJob();
        job.setId(jobId);
        job.setStatus(DeploymentJobStatus.COMPLETED);

        when(deploymentJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        DeploymentJobDto dto = service.getDeploymentById(jobId);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(jobId);
        assertThat(dto.status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("getDeploymentById throws ResourceNotFoundException when missing")
    void getDeploymentById_notFound_throwsException() {
        UUID jobId = UUID.randomUUID();
        when(deploymentJobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDeploymentById(jobId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Deployment job not found with ID: " + jobId);
    }

    @Test
    @DisplayName("retryDeployment triggers retry via workflow engine and records audit")
    void retryDeployment_triggersWorkflowAndAudits() {
        UUID jobId = UUID.randomUUID();
        DeploymentJob job = new DeploymentJob();
        job.setId(jobId);
        job.setStatus(DeploymentJobStatus.RETRY_PENDING);

        when(deploymentJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        DeploymentJobDto dto = service.retryDeployment(jobId, "corr-retry-test");

        assertThat(dto).isNotNull();
        verify(workflowEngine).retryJobWorkflow(jobId);
        verify(auditService).recordAudit(any());
    }

    @Test
    @DisplayName("verifyDeployment delegates to workflowService and returns DTO")
    void verifyDeployment_delegatesToWorkflowService() {
        UUID jobId = UUID.randomUUID();
        DeploymentJob job = new DeploymentJob();
        job.setId(jobId);
        job.setStatus(DeploymentJobStatus.VERIFIED);

        when(deploymentJobRepository.existsById(jobId)).thenReturn(true);
        when(workflowService.verifyDeployment(eq(jobId), eq("corr-verify-test"))).thenReturn(job);

        DeploymentJobDto dto = service.verifyDeployment(jobId, "corr-verify-test");

        assertThat(dto).isNotNull();
        assertThat(dto.status()).isEqualTo("VERIFIED");
        verify(workflowService).verifyDeployment(eq(jobId), eq("corr-verify-test"));
    }
}
