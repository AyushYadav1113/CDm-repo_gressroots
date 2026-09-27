package com.grassroots.cdm.deployment.adapter;

import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Registry resolving the appropriate technology deployment adapter for a job.
 */
@Component
public class DeploymentAdapterRegistry {

    private final List<DeploymentAdapter> adapters;

    public DeploymentAdapterRegistry(List<DeploymentAdapter> adapters) {
        this.adapters = adapters != null ? adapters : List.of();
    }

    public Optional<DeploymentAdapter> getAdapter(ServerTechnology technology) {
        return adapters.stream()
                .filter(a -> a.getSupportedTechnology() == technology)
                .findFirst();
    }

    public Optional<DeploymentAdapter> getAdapterForJob(DeploymentJob job) {
        return adapters.stream()
                .filter(a -> a.supports(job))
                .findFirst();
    }

    public DeploymentAdapter getRequiredAdapter(DeploymentJob job) {
        return getAdapterForJob(job)
                .orElseThrow(() -> new IllegalArgumentException("No deployment adapter registered for job technology: " +
                        (job.getTargetServer() != null ? job.getTargetServer().getTechnology() : "unknown")));
    }
}
