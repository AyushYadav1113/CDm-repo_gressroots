package com.grassroots.cdm.integration.midserver;

import com.grassroots.cdm.entity.enums.DeploymentType;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.MidServerExecutionState;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.integration.midserver.dto.CertificateReferenceDto;
import com.grassroots.cdm.integration.midserver.dto.ExecutionParametersDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerCancelResponseDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobRequestDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobResponseDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerStatusQueryResponseDto;
import com.grassroots.cdm.integration.midserver.dto.TargetServerInfoDto;
import com.grassroots.cdm.integration.midserver.exception.MidServerDuplicateRequestException;
import com.grassroots.cdm.integration.midserver.exception.MidServerUnavailableException;
import com.grassroots.cdm.integration.midserver.mock.MockMidServerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockMidServerClientTest {

    private MockMidServerClient mockClient;

    @BeforeEach
    void setUp() {
        mockClient = new MockMidServerClient();
    }

    private MidServerJobRequestDto createRequest(String hostname, String idempotencyKey) {
        return new MidServerJobRequestDto(
                UUID.randomUUID(),
                idempotencyKey,
                new TargetServerInfoDto(hostname, "192.168.1.10", ServerOperatingSystem.LINUX_RHEL, ServerTechnology.APACHE, 443, EnvironmentType.STAGING),
                DeploymentType.RENEWAL_REPLACEMENT,
                new CertificateReferenceDto(UUID.randomUUID(), "112233", "THUMBPRINT112233", "test.example.com", List.of("test.example.com"), "cyberark://ref", Instant.now().plusSeconds(3600)),
                new ExecutionParametersDto("/etc/apache2/ssl", "default-site", true, false, true, 120, Map.of())
        );
    }

    @Test
    @DisplayName("Mock submit job returns queued receipt and subsequent query returns success")
    void testMockSubmissionAndStatus() {
        MidServerJobRequestDto req = createRequest("web-01.internal", "MOCK-IDEMP-01");
        MidServerJobResponseDto response = mockClient.submitJob(req);

        assertThat(response).isNotNull();
        assertThat(response.getTaskId()).startsWith("MID-MOCK-");
        assertThat(response.getStatus()).isEqualTo(MidServerExecutionState.QUEUED);

        MidServerStatusQueryResponseDto query = mockClient.getJobStatus(response.getTaskId());
        assertThat(query).isNotNull();
        assertThat(query.getState()).isEqualTo(MidServerExecutionState.SUCCESS);
        assertThat(query.getExitCode()).isEqualTo(0);
        assertThat(query.isSuccessful()).isTrue();
    }

    @Test
    @DisplayName("Mock duplicate request throws duplicate exception")
    void testMockDuplicateIdempotency() {
        MidServerJobRequestDto req1 = createRequest("web-01.internal", "MOCK-DUP-KEY");
        mockClient.submitJob(req1);

        MidServerJobRequestDto req2 = createRequest("web-01.internal", "MOCK-DUP-KEY");
        assertThatThrownBy(() -> mockClient.submitJob(req2))
                .isInstanceOf(MidServerDuplicateRequestException.class)
                .hasMessageContaining("MOCK-DUP-KEY");
    }

    @Test
    @DisplayName("Mock failure simulation when hostname contains fail-target")
    void testMockSimulatedFailure() {
        MidServerJobRequestDto req = createRequest("fail-target-host.internal", "MOCK-FAIL-01");
        MidServerJobResponseDto response = mockClient.submitJob(req);

        MidServerStatusQueryResponseDto query = mockClient.getJobStatus(response.getTaskId());
        assertThat(query.getState()).isEqualTo(MidServerExecutionState.FAILED);
        assertThat(query.getExitCode()).isEqualTo(1);
        assertThat(query.isSuccessful()).isFalse();
    }

    @Test
    @DisplayName("Mock outage simulation throws MidServerUnavailableException")
    void testMockOutage() {
        mockClient.setSimulatedHealthy(false);
        MidServerJobRequestDto req = createRequest("web-01.internal", "MOCK-OUTAGE-01");

        assertThatThrownBy(() -> mockClient.submitJob(req))
                .isInstanceOf(MidServerUnavailableException.class);
    }

    @Test
    @DisplayName("Mock job cancellation updates status to CANCELLED")
    void testMockCancelJob() {
        MidServerJobRequestDto req = createRequest("web-01.internal", "MOCK-CANCEL-01");
        MidServerJobResponseDto response = mockClient.submitJob(req);

        MidServerCancelResponseDto cancel = mockClient.cancelJob(response.getTaskId(), "Manual stop");
        assertThat(cancel.getStatus()).isEqualTo("CANCELLED");

        MidServerStatusQueryResponseDto query = mockClient.getJobStatus(response.getTaskId());
        assertThat(query.getState()).isEqualTo(MidServerExecutionState.CANCELLED);
    }
}
