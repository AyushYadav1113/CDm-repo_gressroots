package com.grassroots.cdm.deployment.adapter.linux;

import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.deployment.TargetType;
import com.grassroots.cdm.deployment.adapter.linux.model.LinuxFilePermissions;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import com.grassroots.cdm.integration.midserver.MidServerClient;
import com.grassroots.cdm.integration.midserver.service.MidServerExecutionService;
import com.grassroots.cdm.repository.CertificateInstallationRepository;
import com.grassroots.cdm.repository.DeploymentJobRepository;
import org.springframework.stereotype.Component;

/**
 * Technology deployment adapter for Nginx HTTP/Reverse-Proxy Server on Linux.
 *
 * Implements the 8 conceptual steps:
 * 1. Transfer certificate bundle securely (/etc/ssl/certs/...bundle.crt with mode 0644)
 * 2. Transfer private key securely (/etc/ssl/private/...key with mode 0640/0600)
 * 3. Apply restrictive permissions (root:www-data or root:nginx, mode 0640)
 * 4. Validate configuration syntax (/usr/sbin/nginx -t)
 * 5. Update certificate configuration directives (ssl_certificate, ssl_certificate_key, .cdm-bak backup)
 * 6. Gracefully reload service (/bin/systemctl reload nginx)
 * 7. Verify live endpoint (TLS probe on port 443 matching target thumbprint)
 * 8. Return structured deployment result with sub-step telemetry and automated rollback
 */
@Component
public class NginxDeploymentAdapter extends AbstractLinuxDeploymentAdapter {

    public NginxDeploymentAdapter(
            MidServerClient midServerClient,
            MidServerExecutionService executionService,
            AuditService auditService,
            DeploymentJobRepository deploymentJobRepository,
            CertificateInstallationRepository installationRepository) {
        super(midServerClient, executionService, auditService, deploymentJobRepository, installationRepository);
    }

    @Override
    public ServerTechnology getSupportedTechnology() {
        return ServerTechnology.NGINX;
    }

    @Override
    public TargetType getSupportedTargetType() {
        return TargetType.LINUX_NGINX;
    }

    @Override
    public String getDefaultConfigPath(TargetServer targetServer, String siteName) {
        boolean isDebian = targetServer != null && targetServer.getOperatingSystem() == ServerOperatingSystem.LINUX_UBUNTU;
        String normalizedSite = (siteName == null || siteName.isBlank() || "default".equalsIgnoreCase(siteName))
                ? "default"
                : siteName.trim();

        if (!normalizedSite.endsWith(".conf")) {
            normalizedSite += ".conf";
        }

        if (isDebian) {
            return "/etc/nginx/sites-available/" + normalizedSite;
        } else {
            return "/etc/nginx/conf.d/" + normalizedSite;
        }
    }

    @Override
    public String getDefaultCertificatePath(CertificateRecord cert) {
        String baseName = sanitizeCommonName(cert != null ? cert.getCommonName() : "server");
        return "/etc/ssl/certs/" + baseName + ".bundle.crt";
    }

    @Override
    public String getDefaultPrivateKeyPath(CertificateRecord cert) {
        String baseName = sanitizeCommonName(cert != null ? cert.getCommonName() : "server");
        return "/etc/ssl/private/" + baseName + ".key";
    }

    @Override
    public LinuxFilePermissions getDefaultCertificatePermissions() {
        return LinuxFilePermissions.defaultCertificatePermissions(); // 0644 root:root
    }

    @Override
    public LinuxFilePermissions getDefaultPrivateKeyPermissions(TargetServer targetServer) {
        boolean isDebian = targetServer != null && targetServer.getOperatingSystem() == ServerOperatingSystem.LINUX_UBUNTU;
        String group = isDebian ? "www-data" : "nginx";
        return new LinuxFilePermissions("0640", "root", group);
    }

    @Override
    public String getConfigValidationCommand(TargetServer targetServer) {
        return "/usr/sbin/nginx -t";
    }

    @Override
    public String getServiceReloadCommand(TargetServer targetServer) {
        return "/bin/systemctl reload nginx";
    }

    private String sanitizeCommonName(String commonName) {
        if (commonName == null || commonName.isBlank()) {
            return "server";
        }
        return commonName.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
