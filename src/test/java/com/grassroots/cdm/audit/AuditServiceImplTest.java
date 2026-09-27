package com.grassroots.cdm.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grassroots.cdm.audit.impl.AuditServiceImpl;
import com.grassroots.cdm.entity.AuditLogRecord;
import com.grassroots.cdm.repository.AuditLogRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuditServiceImpl.
 * Validates safe audit emission without secret leakage.
 */
class AuditServiceImplTest {

    private AuditLogRecordRepository repository;
    private AuditServiceImpl auditService;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRecordRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        auditService = new AuditServiceImpl(repository, new ObjectMapper());
    }

    @Test
    void recordAudit_persists_audit_log_record_with_correct_fields() {
        AuditEvent event = AuditEvent.create(
                AuditAction.DEPLOYMENT_DISPATCHED,
                "DeploymentJob",
                "job-123",
                "SCHEDULER",
                "SUCCESS",
                "{\"host\":\"web01.internal\"}",
                "127.0.0.1"
        );

        auditService.recordAudit(event);

        ArgumentCaptor<AuditLogRecord> captor = ArgumentCaptor.forClass(AuditLogRecord.class);
        verify(repository, times(1)).save(captor.capture());

        AuditLogRecord saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("DEPLOYMENT_DISPATCHED");
        assertThat(saved.getEntityName()).isEqualTo("DeploymentJob");
        assertThat(saved.getEntityId()).isEqualTo("job-123");
        assertThat(saved.getActor()).isEqualTo("SCHEDULER");
        assertThat(saved.getOutcome()).isEqualTo("SUCCESS");
        assertThat(saved.getClientIp()).isEqualTo("127.0.0.1");
    }

    @Test
    void recordAudit_does_not_persist_null_event() {
        auditService.recordAudit(null);
        verify(repository, never()).save(any());
    }

    @Test
    void recordAudit_redacts_private_key_in_details() {
        String detailsWithKey = "{\"note\":\"-----BEGIN RSA PRIVATE KEY-----\\nMIIEowIBAAKCAQEA...\\n-----END RSA PRIVATE KEY-----\"}";
        AuditEvent event = AuditEvent.create(
                AuditAction.DEPLOYMENT_DISPATCHED,
                "DeploymentJob",
                "job-key-test",
                "SYSTEM",
                "SUCCESS",
                detailsWithKey,
                "127.0.0.1"
        );

        auditService.recordAudit(event);

        ArgumentCaptor<AuditLogRecord> captor = ArgumentCaptor.forClass(AuditLogRecord.class);
        verify(repository).save(captor.capture());

        String savedDetails = captor.getValue().getDetails();
        assertThat(savedDetails).doesNotContain("BEGIN RSA PRIVATE KEY");
        assertThat(savedDetails).doesNotContain("END RSA PRIVATE KEY");
    }

    @Test
    void recordAudit_redacts_password_patterns_in_details() {
        String detailsWithPassword = "{\"password\":\"supersecret123\", \"host\":\"web01.internal\"}";
        AuditEvent event = AuditEvent.create(
                AuditAction.CREDENTIAL_RETRIEVED,
                "CyberArk",
                "safe/account",
                "WORKFLOW",
                "SUCCESS",
                detailsWithPassword,
                "127.0.0.1"
        );

        auditService.recordAudit(event);

        ArgumentCaptor<AuditLogRecord> captor = ArgumentCaptor.forClass(AuditLogRecord.class);
        verify(repository).save(captor.capture());

        String savedDetails = captor.getValue().getDetails();
        assertThat(savedDetails).doesNotContain("supersecret123");
    }

    @Test
    void recordWorkflowEvent_persists_workflow_audit_record() {
        UUID jobId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        auditService.recordWorkflowEvent(jobId, correlationId, "CREDENTIAL_ACQUISITION", "SUCCESS",
                "CyberArk credentials retrieved for host web01.internal");

        ArgumentCaptor<AuditLogRecord> captor = ArgumentCaptor.forClass(AuditLogRecord.class);
        verify(repository, times(1)).save(captor.capture());

        AuditLogRecord saved = captor.getValue();
        assertThat(saved.getCorrelationId()).isEqualTo(correlationId);
        assertThat(saved.getReferenceId()).isEqualTo(jobId.toString());
        assertThat(saved.getEventType()).isEqualTo("CREDENTIAL_ACQUISITION");
        assertThat(saved.getOutcome()).isEqualTo("SUCCESS");
        assertThat(saved.getMessage()).contains("CyberArk credentials retrieved");
        assertThat(saved.getMessage()).doesNotContain("password");
        assertThat(saved.getMessage()).doesNotContain("secret");
    }

    @Test
    void recordWorkflowEvent_with_metadata_persists_sanitized_metadata() {
        UUID jobId = UUID.randomUUID();
        Map<String, Object> metadata = Map.of(
                "targetHost", "web01.internal",
                "technology", "IIS",
                "password", "shouldBeRedacted"
        );

        auditService.recordWorkflowEvent(jobId, null, "MID_DISPATCH", "SUCCESS",
                "Job dispatched to MID Server", metadata);

        ArgumentCaptor<AuditLogRecord> captor = ArgumentCaptor.forClass(AuditLogRecord.class);
        verify(repository, times(1)).save(captor.capture());

        String savedDetails = captor.getValue().getDetails();
        assertThat(savedDetails).contains("web01.internal");
        assertThat(savedDetails).contains("IIS");
        assertThat(savedDetails).doesNotContain("shouldBeRedacted");
        assertThat(savedDetails).contains("[PROTECTED]");
    }

    @Test
    void recordWorkflowEvent_with_null_jobId_records_GLOBAL_reference() {
        auditService.recordWorkflowEvent(null, "corr-123", "WORKFLOW_STARTED", "SUCCESS", "Global workflow event");

        ArgumentCaptor<AuditLogRecord> captor = ArgumentCaptor.forClass(AuditLogRecord.class);
        verify(repository, times(1)).save(captor.capture());

        assertThat(captor.getValue().getReferenceId()).isEqualTo("GLOBAL");
    }

    @Test
    void sanitize_removes_private_key_block() {
        String input = "key: -----BEGIN PRIVATE KEY-----\nMIIE...\n-----END PRIVATE KEY-----";
        String sanitized = auditService.sanitize(input);
        assertThat(sanitized).doesNotContain("BEGIN PRIVATE KEY");
        assertThat(sanitized).contains("[REDACTED_PRIVATE_KEY]");
    }

    @Test
    void sanitize_returns_null_for_null_input() {
        assertThat(auditService.sanitize(null)).isNull();
    }

    @Test
    void sanitize_returns_blank_for_blank_input() {
        assertThat(auditService.sanitize("")).isEqualTo("");
    }
}
