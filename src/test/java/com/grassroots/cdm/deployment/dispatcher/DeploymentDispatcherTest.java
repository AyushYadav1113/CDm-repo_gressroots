package com.grassroots.cdm.deployment.dispatcher;

import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.dispatcher.impl.DefaultDeploymentDispatcher;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.MidServerExecution;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.DeploymentType;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.InstallationStatus;
import com.grassroots.cdm.entity.enums.MidServerExecutionState;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.integration.midserver.MidServerClient;
import com.grassroots.cdm.integration.midserver.config.MidServerProperties;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobRequestDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobResponseDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerStatusQueryResponseDto;
import com.grassroots.cdm.integration.midserver.service.MidServerExecutionService;
import com.grassroots.cdm.integration.midserver.service.impl.DefaultMidServerExecutionService;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import com.grassroots.cdm.repository.MidServerExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentDispatcherTest {

    private MidServerClient midServerClient;
    private MidServerExecutionRepository executionRepository;
    private DeploymentJobRepository deploymentJobRepository;
    private AuditService auditService;
    private MidServerProperties properties;

    private MidServerExecutionService executionService;
    private DefaultDeploymentDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        midServerClient = mock(MidServerClient.class);
        executionRepository = mock(MidServerExecutionRepository.class);
        deploymentJobRepository = mock(DeploymentJobRepository.class);
        auditService = mock(AuditService.class);

        properties = new MidServerProperties();
        properties.setBaseUrl("https://mid-default:8443");
        properties.setExecutionTimeoutSeconds(240);

        executionService = new DefaultMidServerExecutionService(
                executionRepository,
                deploymentJobRepository,
                midServerClient,
                auditService
        );

        dispatcher = new DefaultDeploymentDispatcher(
                midServerClient,
                executionService,
                properties
        );
    }

    private DeploymentJob buildTestJob() {
        MidServer midServer = new MidServer("MID-CORP-01", "https://mid-corp-01.internal:8443", MidServerStatus.UP);
        TargetServer targetServer = new TargetServer("app01.grassroots.internal", "10.0.2.15", ServerOperatingSystem.LINUX_RHEL, ServerTechnology.NGINX, EnvironmentType.PRODUCTION);
        targetServer.setMidServer(midServer);

        CertificateRecord oldCert = new CertificateRecord();
        oldCert.setId(UUID.randomUUID());
        oldCert.setCommonName("app.grassroots.internal");
        oldCert.setThumbprint("OLDTHUMBPRINT123");

        CertificateRecord newCert = new CertificateRecord();
        newCert.setId(UUID.randomUUID());
        newCert.setCommonName("app.grassroots.internal");
        newCert.setSerialNumber("SN987654321");
        newCert.setThumbprint("NEWTHUMBPRINT456");
        newCert.setSubjectAlternativeNames("app.grassroots.internal, secure.grassroots.internal");
        newCert.setValidTo(Instant.now().plusSeconds(86400 * 90));

        CertificateInstallation installation = new CertificateInstallation(oldCert, targetServer, ServerTechnology.NGINX, "app-site-443", 443);
        installation.setInstallationPath("/etc/nginx/certs/bundle.crt");

        DeploymentJob job = new DeploymentJob();
        job.setId(UUID.randomUUID());
        job.setJobReference("JOB-2026-001");
        job.setIdempotencyKey("IDEMP-DISPATCH-TEST-001");
        job.setTargetServer(targetServer);
        job.setOldCertificate(oldCert);
        job.setNewCertificate(newCert);
        job.setInstallation(installation);
        job.setDeploymentType(DeploymentType.RENEWAL_REPLACEMENT);
        job.setTargetPort(443);

        return job;
    }

    @Test
    @DisplayName("Dispatcher builds contract DTO, calls MidServerClient, and persists execution")
    void testDispatchJobSuccessfully() {
        DeploymentJob job = buildTestJob();

        MidServerJobResponseDto mockResponse = new MidServerJobResponseDto(
                "MID-TASK-777",
                job.getId(),
                job.getIdempotencyKey(),
                MidServerExecutionState.QUEUED,
                Instant.now(),
                "Task queued"
        );

        when(midServerClient.submitJob(eq("https://mid-corp-01.internal:8443"), any(MidServerJobRequestDto.class)))
                .thenReturn(mockResponse);

        MidServerExecution mockSavedExec = new MidServerExecution(job, job.getTargetServer().getMidServer(), "MID-TASK-777", job.getIdempotencyKey(), Instant.now());
        mockSavedExec.setId(UUID.randomUUID());
        when(executionRepository.saveAndFlush(any(MidServerExecution.class))).thenReturn(mockSavedExec);

        String taskId = dispatcher.dispatchJob(job);

        assertThat(taskId).isEqualTo("MID-TASK-777");

        // Verify request payload contract passed to MID Server
        ArgumentCaptor<MidServerJobRequestDto> captor = ArgumentCaptor.forClass(MidServerJobRequestDto.class);
        verify(midServerClient).submitJob(eq("https://mid-corp-01.internal:8443"), captor.capture());

        MidServerJobRequestDto sentReq = captor.getValue();
        assertThat(sentReq.getJobId()).isEqualTo(job.getId());
        assertThat(sentReq.getIdempotencyKey()).isEqualTo(job.getIdempotencyKey());
        assertThat(sentReq.getTargetServer().getHostname()).isEqualTo("app01.grassroots.internal");
        assertThat(sentReq.getCertificateReference().getCommonName()).isEqualTo("app.grassroots.internal");
        assertThat(sentReq.getCertificateReference().getThumbprint()).isEqualTo("NEWTHUMBPRINT456");
        assertThat(sentReq.getCertificateReference().getSubjectAlternativeNames()).contains("app.grassroots.internal", "secure.grassroots.internal");
        assertThat(sentReq.getExecutionParameters().getInstallationPath()).isEqualTo("/etc/nginx/certs/bundle.crt");

        // Verify execution record saved and audit event recorded
        verify(executionRepository).saveAndFlush(any(MidServerExecution.class));
        verify(auditService).recordAudit(any(AuditEvent.class));
    }

    @Test
    @DisplayName("Status polling updates execution record and job started/completed timestamps")
    void testPollAndSyncTaskStatus() {
        DeploymentJob job = buildTestJob();
        MidServer midServer = job.getTargetServer().getMidServer();
        String taskId = "MID-TASK-888";

        MidServerExecution execution = new MidServerExecution(job, midServer, taskId, job.getIdempotencyKey(), Instant.now());
        execution.setId(UUID.randomUUID());
        when(executionRepository.findByTaskId(taskId)).thenReturn(Optional.of(execution));
        when(executionRepository.saveAndFlush(any(MidServerExecution.class))).thenReturn(execution);

        Instant start = Instant.now().minusSeconds(10);
        Instant finish = Instant.now();
        MidServerStatusQueryResponseDto statusDto = new MidServerStatusQueryResponseDto(
                taskId,
                job.getId(),
                job.getIdempotencyKey(),
                MidServerExecutionState.SUCCESS,
                0,
                "Certificate bound successfully",
                null,
                null,
                Instant.now().minusSeconds(15),
                start,
                finish
        );

        when(midServerClient.getJobStatus("https://mid-corp-01.internal:8443", taskId)).thenReturn(statusDto);

        MidServerStatusQueryResponseDto result = executionService.pollAndSyncTaskStatus(taskId);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(execution.getStatus()).isEqualTo(MidServerExecutionState.SUCCESS);
        assertThat(execution.getExitCode()).isEqualTo(0);
        assertThat(execution.getStdoutSummary()).contains("bound successfully");

        // Verify job timestamps updated
        verify(deploymentJobRepository).saveAndFlush(job);
        assertThat(job.getStartedAt()).isEqualTo(start);
    }
}
