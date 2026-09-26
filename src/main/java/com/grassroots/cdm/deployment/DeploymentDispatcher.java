package com.grassroots.cdm.deployment;

import com.grassroots.cdm.entity.DeploymentJob;

/**
 * Dispatcher contract responsible for executing deployment orchestrations
 * by preparing parameters and invoking the MID Server execution layer.
 */
public interface DeploymentDispatcher {

    /**
     * Dispatches a deployment job to the MID Server.
     *
     * @param job DeploymentJob entity to dispatch
     * @return Dispatched task reference ID from the MID Server
     */
    String dispatchJob(DeploymentJob job);
}
