package com.grassroots.cdm.entity;

import com.grassroots.cdm.entity.enums.InstallationStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Entity representing an active certificate installed on a server, port, and application binding.
 */
@Entity
@Table(
        name = "certificate_installations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_cert_installation", columnNames = {"server_id", "port", "binding_info"})
        }
)
public class CertificateInstallation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certificate_id", nullable = false)
    private CertificateRecord certificate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private TargetServer server;

    @Enumerated(EnumType.STRING)
    @Column(name = "technology", nullable = false, length = 50)
    private ServerTechnology technology;

    @Column(name = "binding_info", length = 255)
    private String bindingInfo;

    @Column(name = "installation_path", length = 500)
    private String installationPath;

    @Column(name = "port", nullable = false)
    private int port = 443;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private InstallationStatus status = InstallationStatus.INSTALLED;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    public CertificateInstallation() {
    }

    public CertificateInstallation(CertificateRecord certificate, TargetServer server,
                                   ServerTechnology technology, String bindingInfo, int port) {
        this.certificate = certificate;
        this.server = server;
        this.technology = technology;
        this.bindingInfo = bindingInfo;
        this.port = port;
    }

    public CertificateRecord getCertificate() {
        return certificate;
    }

    public void setCertificate(CertificateRecord certificate) {
        this.certificate = certificate;
    }

    public TargetServer getServer() {
        return server;
    }

    public void setServer(TargetServer server) {
        this.server = server;
    }

    public ServerTechnology getTechnology() {
        return technology;
    }

    public void setTechnology(ServerTechnology technology) {
        this.technology = technology;
    }

    public String getBindingInfo() {
        return bindingInfo;
    }

    public void setBindingInfo(String bindingInfo) {
        this.bindingInfo = bindingInfo;
    }

    public String getInstallationPath() {
        return installationPath;
    }

    public void setInstallationPath(String installationPath) {
        this.installationPath = installationPath;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public InstallationStatus getStatus() {
        return status;
    }

    public void setStatus(InstallationStatus status) {
        this.status = status;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(Instant lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }
}
