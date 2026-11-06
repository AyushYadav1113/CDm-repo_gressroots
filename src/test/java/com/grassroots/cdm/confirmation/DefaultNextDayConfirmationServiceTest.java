package com.grassroots.cdm.confirmation;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.confirmation.impl.DefaultNextDayConfirmationService;
import com.grassroots.cdm.confirmation.model.NextDayConfirmationResult;
import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.integration.servicenow.ServiceNowClient;
import com.grassroots.cdm.integration.servicenow.dto.ServiceNowCertificateDto;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultNextDayConfirmationServiceTest {

    @Mock
    private DeploymentJobRepository jobRepository;

    @Mock
    private ServiceNowClient serviceNowClient;

    @Mock
    private AuditService auditService;

    private DefaultNextDayConfirmationService service;

    private static final String NEW_THUMBPRINT = "A1B2C3D4E5F60718293A4B5C6D7E8F9012345678";
    private static final String OLD_THUMBPRINT = "11223344556677889900AABBCCDDEEFF00112233";
    private static final String HOST = "app-prod-01.internal.grassroots.org";
    private static final String CORRELATION_ID = "test-corr-12345";

    @BeforeEach
    void setUp() {
        service = new DefaultNextDayConfirmationService(jobRepository, serviceNowClient, auditService);
    }

    private DeploymentJob createJob(UUID jobId, DeploymentJobStatus status, String host,
                                   String newThumbprint, String oldThumbprint) {
        DeploymentJob job = new DeploymentJob();
        job.setId(jobId);
        job.setStatus(status);
        job.setTargetHost(host);

        if (newThumbprint != null) {
            CertificateRecord newCert = new CertificateRecord();
            newCert.setId(UUID.randomUUID());
            newCert.setThumbprint(newThumbprint);
            job.setNewCertificate(newCert);
        }

        if (oldThumbprint != null) {
            CertificateRecord oldCert = new CertificateRecord();
            oldCert.setId(UUID.randomUUID());
            oldCert.setThumbprint(oldThumbprint);
            job.setOldCertificate(oldCert);
        }

        return job;
    }

    private ServiceNowCertificateDto createDto(String sysId, String name, String host, String thumbprint) {
        ServiceNowCertificateDto dto = new ServiceNowCertificateDto();
        dto.setSysId(sysId);
        dto.setName(name);
        dto.setTargetHost(host);
        dto.setThumbprint(thumbprint);
        return dto;
    }

    @Nested
    @DisplayName("Validation and Guard Conditions")
    class ValidationGuards {

        @Test
        @DisplayName("Throws IllegalArgumentException when jobId is null")
        void confirmDeployment_nullJobId_throwsException() {
            assertThatThrownBy(() -> service.confirmDeployment(null, CORRELATION_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("jobId must not be null");
        }

        @Test
        @DisplayName("Returns DISCOVERY_UNAVAILABLE when job is not found in database")
        void confirmDeployment_jobNotFound_returnsDiscoveryUnavailable() {
            UUID jobId = UUID.randomUUID();
            when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

            NextDayConfirmationResult result = service.confirmDeployment(jobId, CORRELATION_ID);

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.DISCOVERY_UNAVAILABLE);
            assertThat(result.getMessage()).contains("Deployment job not found");
            assertThat(result.getJobId()).isEqualTo(jobId);
        }

        @Test
        @DisplayName("Returns PENDING_CONFIRMATION when job is not in VERIFIED or COMPLETED status")
        void confirmDeployment_ineligibleStatus_returnsPendingConfirmation() {
            UUID jobId = UUID.randomUUID();
            DeploymentJob job = createJob(jobId, DeploymentJobStatus.SENT_TO_MID, HOST, NEW_THUMBPRINT, OLD_THUMBPRINT);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            NextDayConfirmationResult result = service.confirmDeployment(jobId, CORRELATION_ID);

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.PENDING_CONFIRMATION);
            assertThat(result.getMessage()).contains("pending");
            verify(serviceNowClient, never()).fetchCertificates(anyString(), anyInt(), anyInt(), anyString());
        }

        @Test
        @DisplayName("Returns DISCOVERY_UNAVAILABLE when job has no new certificate")
        void confirmDeployment_noNewCert_returnsDiscoveryUnavailable() {
            UUID jobId = UUID.randomUUID();
            DeploymentJob job = createJob(jobId, DeploymentJobStatus.VERIFIED, HOST, null, OLD_THUMBPRINT);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            NextDayConfirmationResult result = service.confirmDeployment(jobId, CORRELATION_ID);

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.DISCOVERY_UNAVAILABLE);
            assertThat(result.getMessage()).contains("no associated new certificate");
            verify(serviceNowClient, never()).fetchCertificates(anyString(), anyInt(), anyInt(), anyString());
        }

        @Test
        @DisplayName("Returns DISCOVERY_UNAVAILABLE when job has null target host")
        void confirmDeployment_nullTargetHost_returnsDiscoveryUnavailable() {
            UUID jobId = UUID.randomUUID();
            DeploymentJob job = createJob(jobId, DeploymentJobStatus.VERIFIED, null, NEW_THUMBPRINT, OLD_THUMBPRINT);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            NextDayConfirmationResult result = service.confirmDeployment(jobId, CORRELATION_ID);

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.DISCOVERY_UNAVAILABLE);
            assertThat(result.getMessage()).contains("no target host");
            verify(serviceNowClient, never()).fetchCertificates(anyString(), anyInt(), anyInt(), anyString());
        }

        @Test
        @DisplayName("Returns DISCOVERY_UNAVAILABLE when job has blank target host")
        void confirmDeployment_blankTargetHost_returnsDiscoveryUnavailable() {
            UUID jobId = UUID.randomUUID();
            DeploymentJob job = createJob(jobId, DeploymentJobStatus.COMPLETED, "   ", NEW_THUMBPRINT, OLD_THUMBPRINT);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            NextDayConfirmationResult result = service.confirmDeployment(jobId, CORRELATION_ID);

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.DISCOVERY_UNAVAILABLE);
            assertThat(result.getMessage()).contains("no target host");
        }
    }

    @Nested
    @DisplayName("ServiceNow Discovery Query and Outcome Determinations")
    class ConfirmationOutcomes {

        @Test
        @DisplayName("Returns CONFIRMED when ServiceNow CMDB reflects the expected new certificate")
        void confirmDeployment_newCertObserved_returnsConfirmed() {
            UUID jobId = UUID.randomUUID();
            DeploymentJob job = createJob(jobId, DeploymentJobStatus.VERIFIED, HOST, NEW_THUMBPRINT, OLD_THUMBPRINT);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            ServiceNowCertificateDto dto = createDto("sys-cert-001", "app.grassroots.org", HOST, NEW_THUMBPRINT);
            when(serviceNowClient.fetchCertificates(anyString(), eq(10), eq(0), eq(CORRELATION_ID)))
                    .thenReturn(List.of(dto));

            NextDayConfirmationResult result = service.confirmDeployment(jobId, CORRELATION_ID);

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
            assertThat(result.isConfirmed()).isTrue();
            assertThat(result.getObservedThumbprint()).isEqualTo(NEW_THUMBPRINT);
            assertThat(result.getExpectedThumbprint()).isEqualTo(NEW_THUMBPRINT);
            assertThat(result.getServiceNowCiSysId()).isEqualTo("sys-cert-001");
            assertThat(result.getMessage()).contains("confirms new certificate");

            verify(auditService).recordWorkflowEvent(
                    eq(jobId),
                    eq(CORRELATION_ID),
                    eq(AuditAction.NEXT_DAY_CONFIRMED.name()),
                    eq("CONFIRMED"),
                    anyString(),
                    any()
            );
        }

        @Test
        @DisplayName("Returns CONFIRMED with colon-formatted or lowercase thumbprint normalization")
        void confirmDeployment_formattedThumbprint_returnsConfirmed() {
            UUID jobId = UUID.randomUUID();
            DeploymentJob job = createJob(jobId, DeploymentJobStatus.COMPLETED, HOST, "AA:BB:CC:DD:EE:FF", OLD_THUMBPRINT);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            // ServiceNow returns lowercase without colons
            ServiceNowCertificateDto dto = createDto("sys-cert-002", "app.grassroots.org", HOST, "aabbccddeeff");
            when(serviceNowClient.fetchCertificates(anyString(), eq(10), eq(0), eq(CORRELATION_ID)))
                    .thenReturn(List.of(dto));

            NextDayConfirmationResult result = service.confirmDeployment(jobId, CORRELATION_ID);

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
            assertThat(result.isConfirmed()).isTrue();
        }

        @Test
        @DisplayName("Returns CONFIRMATION_FAILED when ServiceNow CMDB still has the OLD certificate")
        void confirmDeployment_oldCertObserved_returnsConfirmationFailed() {
            UUID jobId = UUID.randomUUID();
            DeploymentJob job = createJob(jobId, DeploymentJobStatus.VERIFIED, HOST, NEW_THUMBPRINT, OLD_THUMBPRINT);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            ServiceNowCertificateDto dto = createDto("sys-cert-old", "old-cert", HOST, OLD_THUMBPRINT);
            when(serviceNowClient.fetchCertificates(anyString(), eq(10), eq(0), eq(CORRELATION_ID)))
                    .thenReturn(List.of(dto));

            NextDayConfirmationResult result = service.confirmDeployment(jobId, CORRELATION_ID);

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMATION_FAILED);
            assertThat(result.isFailed()).isTrue();
            assertThat(result.isConfirmed()).isFalse();
            assertThat(result.getObservedThumbprint()).isEqualTo(OLD_THUMBPRINT);
            assertThat(result.getMessage()).contains("Old certificate still present in CMDB");

            verify(auditService).recordWorkflowEvent(
                    eq(jobId),
                    eq(CORRELATION_ID),
                    eq(AuditAction.NEXT_DAY_CONFIRMATION_FAILED.name()),
                    eq("FAILED"),
                    anyString(),
                    any()
            );
        }

        @Test
        @DisplayName("Returns CONFIRMATION_FAILED when ServiceNow CMDB returns an unexpected / mismatched certificate")
        void confirmDeployment_unrecognizedCert_returnsConfirmationFailed() {
            UUID jobId = UUID.randomUUID();
            DeploymentJob job = createJob(jobId, DeploymentJobStatus.VERIFIED, HOST, NEW_THUMBPRINT, OLD_THUMBPRINT);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            String unexpectedThumbprint = "9999999999999999999999999999999999999999";
            ServiceNowCertificateDto dto = createDto("sys-cert-unknown", "unknown-cert", HOST, unexpectedThumbprint);
            when(serviceNowClient.fetchCertificates(anyString(), eq(10), eq(0), eq(CORRELATION_ID)))
                    .thenReturn(List.of(dto));

            NextDayConfirmationResult result = service.confirmDeployment(jobId, CORRELATION_ID);

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMATION_FAILED);
            assertThat(result.isFailed()).isTrue();
            assertThat(result.getObservedThumbprint()).isEqualTo(unexpectedThumbprint);
            assertThat(result.getMessage()).contains("Unexpected certificate in CMDB");
        }

        @Test
        @DisplayName("Returns DISCOVERY_UNAVAILABLE when ServiceNow CMDB returns empty list")
        void confirmDeployment_emptyCmdbResponse_returnsDiscoveryUnavailable() {
            UUID jobId = UUID.randomUUID();
            DeploymentJob job = createJob(jobId, DeploymentJobStatus.VERIFIED, HOST, NEW_THUMBPRINT, OLD_THUMBPRINT);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            when(serviceNowClient.fetchCertificates(anyString(), eq(10), eq(0), eq(CORRELATION_ID)))
                    .thenReturn(Collections.emptyList());

            NextDayConfirmationResult result = service.confirmDeployment(jobId, CORRELATION_ID);

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.DISCOVERY_UNAVAILABLE);
            assertThat(result.isUnavailable()).isTrue();
            assertThat(result.isConfirmed()).isFalse();
            assertThat(result.getMessage()).contains("ServiceNow returned no CMDB records");

            verify(auditService).recordWorkflowEvent(
                    eq(jobId),
                    eq(CORRELATION_ID),
                    eq(AuditAction.NEXT_DAY_DISCOVERY_UNAVAILABLE.name()),
                    eq("UNAVAILABLE"),
                    anyString(),
                    any()
            );
        }

        @Test
        @DisplayName("Returns DISCOVERY_UNAVAILABLE when ServiceNow CMDB returns null")
        void confirmDeployment_nullCmdbResponse_returnsDiscoveryUnavailable() {
            UUID jobId = UUID.randomUUID();
            DeploymentJob job = createJob(jobId, DeploymentJobStatus.COMPLETED, HOST, NEW_THUMBPRINT, OLD_THUMBPRINT);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            when(serviceNowClient.fetchCertificates(anyString(), eq(10), eq(0), eq(CORRELATION_ID)))
                    .thenReturn(null);

            NextDayConfirmationResult result = service.confirmDeployment(jobId, CORRELATION_ID);

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.DISCOVERY_UNAVAILABLE);
            assertThat(result.isUnavailable()).isTrue();
        }

        @Test
        @DisplayName("Returns DISCOVERY_UNAVAILABLE when ServiceNow client throws an exception")
        void confirmDeployment_clientThrowsException_returnsDiscoveryUnavailable() {
            UUID jobId = UUID.randomUUID();
            DeploymentJob job = createJob(jobId, DeploymentJobStatus.VERIFIED, HOST, NEW_THUMBPRINT, OLD_THUMBPRINT);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            when(serviceNowClient.fetchCertificates(anyString(), eq(10), eq(0), eq(CORRELATION_ID)))
                    .thenThrow(new RuntimeException("Connection timed out to ServiceNow instance"));

            NextDayConfirmationResult result = service.confirmDeployment(jobId, CORRELATION_ID);

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.DISCOVERY_UNAVAILABLE);
            assertThat(result.isUnavailable()).isTrue();
            assertThat(result.getMessage()).contains("ServiceNow query failed: Connection timed out");
        }

        @Test
        @DisplayName("Auditing exception does not prevent confirmation determination from returning")
        void confirmDeployment_auditThrows_stillReturnsResult() {
            UUID jobId = UUID.randomUUID();
            DeploymentJob job = createJob(jobId, DeploymentJobStatus.VERIFIED, HOST, NEW_THUMBPRINT, OLD_THUMBPRINT);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            ServiceNowCertificateDto dto = createDto("sys-01", "cert", HOST, NEW_THUMBPRINT);
            when(serviceNowClient.fetchCertificates(anyString(), eq(10), eq(0), eq(CORRELATION_ID)))
                    .thenReturn(List.of(dto));

            doThrow(new RuntimeException("Database error during audit")).when(auditService)
                    .recordWorkflowEvent(any(), any(), any(), any(), any(), any());

            NextDayConfirmationResult result = service.confirmDeployment(jobId, CORRELATION_ID);

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
            assertThat(result.isConfirmed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Batch Processing and Re-check")
    class BatchAndRecheck {

        @Test
        @DisplayName("processAllPendingConfirmations returns empty list when no jobs are eligible")
        void processAll_noEligibleJobs_returnsEmptyList() {
            when(jobRepository.findByStatus(DeploymentJobStatus.VERIFIED)).thenReturn(List.of());
            when(jobRepository.findByStatus(DeploymentJobStatus.COMPLETED)).thenReturn(List.of());

            List<NextDayConfirmationResult> results = service.processAllPendingConfirmations(CORRELATION_ID);

            assertThat(results).isEmpty();
            verify(serviceNowClient, never()).fetchCertificates(anyString(), anyInt(), anyInt(), anyString());
        }

        @Test
        @DisplayName("processAllPendingConfirmations processes all VERIFIED and COMPLETED jobs")
        void processAll_multipleEligibleJobs_processesEach() {
            UUID job1Id = UUID.randomUUID();
            UUID job2Id = UUID.randomUUID();

            DeploymentJob job1 = createJob(job1Id, DeploymentJobStatus.VERIFIED, HOST, NEW_THUMBPRINT, OLD_THUMBPRINT);
            DeploymentJob job2 = createJob(job2Id, DeploymentJobStatus.COMPLETED, "web-02.grassroots.org", NEW_THUMBPRINT, OLD_THUMBPRINT);

            when(jobRepository.findByStatus(DeploymentJobStatus.VERIFIED)).thenReturn(List.of(job1));
            when(jobRepository.findByStatus(DeploymentJobStatus.COMPLETED)).thenReturn(List.of(job2));

            ServiceNowCertificateDto dto = createDto("sys-01", "cert", HOST, NEW_THUMBPRINT);
            when(serviceNowClient.fetchCertificates(anyString(), eq(10), eq(0), anyString()))
                    .thenReturn(List.of(dto));

            List<NextDayConfirmationResult> results = service.processAllPendingConfirmations(CORRELATION_ID);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
            assertThat(results.get(1).getStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
        }

        @Test
        @DisplayName("recheckConfirmation delegates to confirmDeployment")
        void recheckConfirmation_delegatesSuccessfully() {
            UUID jobId = UUID.randomUUID();
            DeploymentJob job = createJob(jobId, DeploymentJobStatus.VERIFIED, HOST, NEW_THUMBPRINT, OLD_THUMBPRINT);
            when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

            ServiceNowCertificateDto dto = createDto("sys-01", "cert", HOST, NEW_THUMBPRINT);
            when(serviceNowClient.fetchCertificates(anyString(), eq(10), eq(0), eq(CORRELATION_ID)))
                    .thenReturn(List.of(dto));

            NextDayConfirmationResult result = service.recheckConfirmation(jobId, CORRELATION_ID);

            assertThat(result.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
            assertThat(result.getJobId()).isEqualTo(jobId);
        }
    }
}
