package com.grassroots.cdm.deployment.adapter;

import com.grassroots.cdm.deployment.TargetType;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.enums.ServerTechnology;

/**
 * Technology-specific deployment adapter contract.
 *
 * Encapsulates technology parameters, step execution orchestration, validation,
 * failure recovery, and rollback mechanisms.
 *
 * Direct execution is strictly delegated to the ServiceNow MID Server boundary;
 * the CDM application never executes arbitrary local or remote shell commands directly.
 */
public interface DeploymentAdapter {

    /**
     * The primary server technology managed by this adapter.
     */
    ServerTechnology getSupportedTechnology();

    /**
     * The target runtime type classification.
     */
    TargetType getSupportedTargetType();

    /**
     * Determines whether this adapter can execute deployment for the specified job.
     */
    boolean supports(DeploymentJob job);

    /**
     * Validates target server readiness, certificate availability, port bindings,
     * and network connectivity prior to dispatching commands.
     *
     * @param job Job to validate
     * @throws RuntimeException if prerequisites are not met
     */
    void validate(DeploymentJob job);

    /**
     * Executes the target technology deployment workflow via the MID Server execution layer.
     *
     * @param job DeploymentJob containing target details and certificate reference
     * @return Detailed execution outcome with step-by-step telemetry
     */
    DeploymentAdapterResult deploy(DeploymentJob job);

    /**
     * Executes rollback on the target server to restore the previous certificate binding
     * when deployment or verification fails.
     *
     * @param job DeploymentJob being rolled back
     * @param failureReason Reason triggering rollback
     * @return Rollback execution outcome
     */
    DeploymentAdapterResult rollback(DeploymentJob job, String failureReason);
}
