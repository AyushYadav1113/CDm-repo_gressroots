package com.grassroots.cdm.deployment.workflow.statemachine;

import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.entity.DeploymentJob;

import java.util.Set;

/**
 * State machine contract for validating, controlling, and persisting
 * DeploymentJob lifecycle state transitions.
 */
public interface DeploymentStateMachine {

    /**
     * Checks if a transition from source state to target state is legally permitted.
     *
     * @param from current status
     * @param to   target status
     * @return true if valid, false otherwise
     */
    boolean canTransition(DeploymentJobStatus from, DeploymentJobStatus to);

    /**
     * Returns the set of valid next states reachable from the given status.
     *
     * @param current current status
     * @return set of permitted destination states
     */
    Set<DeploymentJobStatus> getValidNextStates(DeploymentJobStatus current);

    /**
     * Validates and executes a state transition, updating timestamps, persisting changes,
     * and recording tamper-evident audit logs.
     *
     * @param job           the job entity to transition
     * @param targetStatus  the desired destination state
     * @param reason        explanation for the state transition
     * @param actor         user or system identity initiating the change
     * @param correlationId tracing correlation ID
     * @return the persisted DeploymentJob in the new state
     */
    DeploymentJob transition(DeploymentJob job, DeploymentJobStatus targetStatus, String reason, String actor, String correlationId);

    /**
     * Overloaded transition method with default actor and automatic correlation ID resolution.
     *
     * @param job          the job entity to transition
     * @param targetStatus the desired destination state
     * @param reason       explanation for the state transition
     * @param actor        user or system identity initiating the change
     * @return the persisted DeploymentJob in the new state
     */
    DeploymentJob transition(DeploymentJob job, DeploymentJobStatus targetStatus, String reason, String actor);
}
