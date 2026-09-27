package com.grassroots.cdm.integration.midserver.mock;

import com.grassroots.cdm.entity.enums.MidServerExecutionState;
import com.grassroots.cdm.integration.midserver.MidServerClient;
import com.grassroots.cdm.integration.midserver.dto.MidServerCancelResponseDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerHealthDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobRequestDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerJobResponseDto;
import com.grassroots.cdm.integration.midserver.dto.MidServerStatusQueryResponseDto;
import com.grassroots.cdm.integration.midserver.exception.MidServerDuplicateRequestException;
import com.grassroots.cdm.integration.midserver.exception.MidServerException;
import com.grassroots.cdm.integration.midserver.exception.MidServerUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory simulated MID Server client for local development and offline testing.
 * Automatically activates when {@code cdm.midserver.mock-enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "cdm.midserver.mock-enabled", havingValue = "true")
public class MockMidServerClient implements MidServerClient {

    private static final Logger log = LoggerFactory.getLogger(MockMidServerClient.class);

    private final Map<String, MidServerStatusQueryResponseDto> tasks = new ConcurrentHashMap<>();
    private final Map<String, String> idempotencyIndex = new ConcurrentHashMap<>();
    private boolean simulatedHealthy = true;

    @Override
    public MidServerJobResponseDto submitJob(MidServerJobRequestDto request) {
        return submitJob("http://mock-mid-server:8080", request);
    }

    @Override
    public MidServerJobResponseDto submitJob(String endpoint, MidServerJobRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("MidServerJobRequestDto cannot be null");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new IllegalArgumentException("IdempotencyKey is required");
        }

        if (!simulatedHealthy) {
            throw new MidServerUnavailableException("Simulated MID Server outage", endpoint);
        }

        String existingTaskId = idempotencyIndex.putIfAbsent(request.getIdempotencyKey(), "PENDING");
        if (existingTaskId != null && !existingTaskId.equals("PENDING")) {
            log.warn("Mock MID Server detected duplicate submission for idempotency key: {}", request.getIdempotencyKey());
            throw new MidServerDuplicateRequestException("Duplicate request for idempotency key: " + request.getIdempotencyKey(), request.getIdempotencyKey(), existingTaskId);
        }

        String hostname = request.getTargetServer() != null ? request.getTargetServer().getHostname() : "";
        if (hostname.contains("unavailable-mid")) {
            idempotencyIndex.remove(request.getIdempotencyKey());
            throw new MidServerUnavailableException("Target MID server simulated as unavailable", endpoint);
        }

        String taskId = "MID-MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        idempotencyIndex.put(request.getIdempotencyKey(), taskId);

        Instant now = Instant.now();
        MidServerExecutionState outcomeState = MidServerExecutionState.SUCCESS;
        int exitCode = 0;
        String stdout = "Mock installation completed successfully. Binding active on port " +
                (request.getTargetServer() != null ? request.getTargetServer().getTargetPort() : 443);
        String stderr = null;
        String errMsg = null;

        if (hostname.contains("fail-target")) {
            outcomeState = MidServerExecutionState.FAILED;
            exitCode = 1;
            stdout = "Starting deployment script...\nFailed to bind port";
            stderr = "System Error: Access Denied or Port Conflict";
            errMsg = "Command execution returned exit code 1";
        }

        MidServerStatusQueryResponseDto statusDto = new MidServerStatusQueryResponseDto(
                taskId,
                request.getJobId(),
                request.getIdempotencyKey(),
                outcomeState,
                exitCode,
                stdout,
                stderr,
                errMsg,
                now,
                now.plusMillis(100),
                now.plusMillis(500)
        );

        tasks.put(taskId, statusDto);
        log.info("Mock MID Server accepted job: taskId={}, jobId={}, idempotencyKey={}",
                taskId, request.getJobId(), request.getIdempotencyKey());

        return new MidServerJobResponseDto(
                taskId,
                request.getJobId(),
                request.getIdempotencyKey(),
                MidServerExecutionState.QUEUED,
                now,
                "Job queued successfully in Mock MID Server queue"
        );
    }

    @Override
    public MidServerStatusQueryResponseDto getJobStatus(String taskId) {
        return getJobStatus("http://mock-mid-server:8080", taskId);
    }

    @Override
    public MidServerStatusQueryResponseDto getJobStatus(String endpoint, String taskId) {
        if (!simulatedHealthy) {
            throw new MidServerUnavailableException("Simulated MID Server outage", endpoint);
        }

        MidServerStatusQueryResponseDto status = tasks.get(taskId);
        if (status == null) {
            throw new MidServerException("Task not found in Mock MID Server: " + taskId);
        }
        return status;
    }

    @Override
    public MidServerCancelResponseDto cancelJob(String taskId, String reason) {
        return cancelJob("http://mock-mid-server:8080", taskId, reason);
    }

    @Override
    public MidServerCancelResponseDto cancelJob(String endpoint, String taskId, String reason) {
        MidServerStatusQueryResponseDto status = tasks.get(taskId);
        if (status != null) {
            status.setState(MidServerExecutionState.CANCELLED);
            status.setErrorMessage("Cancelled: " + reason);
        }
        return new MidServerCancelResponseDto(taskId, "CANCELLED", Instant.now(), "Task cancelled successfully");
    }

    @Override
    public boolean checkHealth() {
        return simulatedHealthy;
    }

    @Override
    public boolean checkHealth(String endpoint) {
        return simulatedHealthy;
    }

    @Override
    public MidServerHealthDto getHealthDetails() {
        return getHealthDetails("http://mock-mid-server:8080");
    }

    @Override
    public MidServerHealthDto getHealthDetails(String endpoint) {
        return new MidServerHealthDto(simulatedHealthy ? "UP" : "DOWN", "mock-1.0.0", tasks.size());
    }

    public void setSimulatedHealthy(boolean simulatedHealthy) {
        this.simulatedHealthy = simulatedHealthy;
    }

    public void clear() {
        tasks.clear();
        idempotencyIndex.clear();
        simulatedHealthy = true;
    }
}
