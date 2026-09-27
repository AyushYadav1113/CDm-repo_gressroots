package com.grassroots.cdm.deployment.adapter;

import com.grassroots.cdm.deployment.TargetType;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentAdapterRegistryTest {

    @Test
    @DisplayName("Registry resolves registered technology adapter")
    void testRegistryResolvesAdapter() {
        DeploymentAdapter iisAdapter = mock(DeploymentAdapter.class);
        when(iisAdapter.getSupportedTechnology()).thenReturn(ServerTechnology.IIS);
        when(iisAdapter.getSupportedTargetType()).thenReturn(TargetType.WINDOWS_IIS);

        DeploymentAdapterRegistry registry = new DeploymentAdapterRegistry(List.of(iisAdapter));

        Optional<DeploymentAdapter> resolved = registry.getAdapter(ServerTechnology.IIS);
        assertThat(resolved).isPresent();
        assertThat(resolved.get()).isSameAs(iisAdapter);

        Optional<DeploymentAdapter> missing = registry.getAdapter(ServerTechnology.APACHE);
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("Registry resolves adapter for job")
    void testRegistryResolvesForJob() {
        DeploymentAdapter iisAdapter = mock(DeploymentAdapter.class);
        DeploymentJob job = new DeploymentJob();
        TargetServer server = new TargetServer("win01", "10.0.1.1", ServerOperatingSystem.WINDOWS_SERVER, ServerTechnology.IIS, EnvironmentType.PRODUCTION);
        job.setTargetServer(server);

        when(iisAdapter.supports(job)).thenReturn(true);

        DeploymentAdapterRegistry registry = new DeploymentAdapterRegistry(List.of(iisAdapter));
        assertThat(registry.getAdapterForJob(job)).contains(iisAdapter);
        assertThat(registry.getRequiredAdapter(job)).isSameAs(iisAdapter);
    }

    @Test
    @DisplayName("Registry throws when required adapter is missing")
    void testRegistryThrowsWhenMissing() {
        DeploymentAdapterRegistry registry = new DeploymentAdapterRegistry(List.of());
        DeploymentJob job = new DeploymentJob();
        TargetServer server = new TargetServer("lnx01", "10.0.1.2", ServerOperatingSystem.LINUX_RHEL, ServerTechnology.APACHE, EnvironmentType.PRODUCTION);
        job.setTargetServer(server);

        assertThatThrownBy(() -> registry.getRequiredAdapter(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No deployment adapter registered");
    }

    @Test
    @DisplayName("Registry resolves Apache and Nginx adapters")
    void testRegistryResolvesApacheAndNginx() {
        DeploymentAdapter apacheAdapter = mock(DeploymentAdapter.class);
        when(apacheAdapter.getSupportedTechnology()).thenReturn(ServerTechnology.APACHE);
        when(apacheAdapter.getSupportedTargetType()).thenReturn(TargetType.LINUX_APACHE);

        DeploymentAdapter nginxAdapter = mock(DeploymentAdapter.class);
        when(nginxAdapter.getSupportedTechnology()).thenReturn(ServerTechnology.NGINX);
        when(nginxAdapter.getSupportedTargetType()).thenReturn(TargetType.LINUX_NGINX);

        DeploymentAdapterRegistry registry = new DeploymentAdapterRegistry(List.of(apacheAdapter, nginxAdapter));

        assertThat(registry.getAdapter(ServerTechnology.APACHE)).contains(apacheAdapter);
        assertThat(registry.getAdapter(ServerTechnology.NGINX)).contains(nginxAdapter);
        assertThat(registry.getAdapter(ServerTechnology.IIS)).isEmpty();
    }
}
