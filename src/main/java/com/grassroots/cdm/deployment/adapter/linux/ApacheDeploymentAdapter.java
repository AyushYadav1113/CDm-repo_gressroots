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
 * Technology deployment adapter for Apache HTTP Server on Linux (Ubuntu/Debian and RHEL/CentOS).
 *
 * Implements the 8 conceptual steps:
 * 1. Transfer certificate securely (/etc/ssl/certs/... with mode 0644)
 * 2. Transfer private key securely (/etc/ssl/private/... with mode 0640/0600)
 * 3. Apply restrictive permissions (root:ssl-cert or root:apache, mode 0640)
 * 4. Validate configuration (apache2ctl configtest / httpd -t)
 * 5. Update certificate configuration (SSLCertificateFile, SSLCertificateKeyFile directives, .cdm-bak backup)
 * 6. Gracefully reload service (systemctl reload apache2 / httpd)
 * 7. Verify live endpoint (TLS probe on port 443 matching target thumbprint)
 * 8. Return structured deployment result with sub-step telemetry and automated rollback
 */
@Component
public class ApacheDeploymentAdapter extends AbstractLinuxDeploymentAdapter {

    public ApacheDeploymentAdapter(
            MidServerClient midServerClient,
            MidServerExecutionService executionService,
            AuditService auditService,
            DeploymentJobRepository deploymentJobRepository,
            CertificateInstallationRepository installationRepository) {
        super(midServerClient, executionService, auditService, deploymentJobRepository, installationRepository);
    }

    @Override
    public ServerTechnology getSupportedTechnology() {
        return ServerTechnology.APACHE;
    }

    @Override
    public TargetType getSupportedTargetType() {
        return TargetType.LINUX_APACHE;
    }

    @Override
    public String getDefaultConfigPath(TargetServer targetServer, String siteName) {
        boolean isDebian = targetServer != null && targetServer.getOperatingSystem() == ServerOperatingSystem.LINUX_UBUNTU;
        String normalizedSite = (siteName == null || siteName.isBlank() || "default".equalsIgnoreCase(siteName))
                ? (isDebian ? "000-default-le-ssl" : "ssl")
                : siteName.trim();

        if (!normalizedSite.endsWith(".conf")) {
            normalizedSite += ".conf";
        }

        if (isDebian) {
            return "/etc/apache2/sites-available/" + normalizedSite;
        } else {
            return "/etc/httpd/conf.d/" + normalizedSite;
        }
    }

    @Override
    public String getDefaultCertificatePath(CertificateRecord cert) {
        String baseName = sanitizeCommonName(cert != null ? cert.getCommonName() : "server");
        return "/etc/ssl/certs/" + baseName + ".crt";
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
        String group = isDebian ? "ssl-cert" : "apache";
        return new LinuxFilePermissions("0640", "root", group);
    }

    @Override
    public String getConfigValidationCommand(TargetServer targetServer) {
        boolean isDebian = targetServer != null && targetServer.getOperatingSystem() == ServerOperatingSystem.LINUX_UBUNTU;
        return isDebian ? "/usr/sbin/apache2ctl configtest" : "/usr/sbin/httpd -t";
    }

    @Override
    public String getServiceReloadCommand(TargetServer targetServer) {
        boolean isDebian = targetServer != null && targetServer.getOperatingSystem() == ServerOperatingSystem.LINUX_UBUNTU;
        return isDebian ? "/bin/systemctl reload apache2" : "/bin/systemctl reload httpd";
    }

    private String sanitizeCommonName(String commonName) {
        if (commonName == null || commonName.isBlank()) {
            return "server";
        }
        return commonName.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
