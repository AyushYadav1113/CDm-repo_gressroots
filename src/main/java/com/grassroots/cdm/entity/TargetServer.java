package com.grassroots.cdm.entity;

import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entity representing target application and infrastructure servers.
 */
@Entity
@Table(name = "target_servers")
public class TargetServer extends BaseEntity {

    @Column(name = "hostname", nullable = false, unique = true)
    private String hostname;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "operating_system", nullable = false, length = 50)
    private ServerOperatingSystem operatingSystem;

    @Enumerated(EnumType.STRING)
    @Column(name = "technology", nullable = false, length = 50)
    private ServerTechnology technology;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 50)
    private EnvironmentType environment = EnvironmentType.PRODUCTION;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mid_server_id")
    private MidServer midServer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ServerStatus status = ServerStatus.ACTIVE;

    public TargetServer() {
    }

    public TargetServer(String hostname, String ipAddress, ServerOperatingSystem operatingSystem,
                        ServerTechnology technology, EnvironmentType environment) {
        this.hostname = hostname;
        this.ipAddress = ipAddress;
        this.operatingSystem = operatingSystem;
        this.technology = technology;
        this.environment = environment;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public ServerOperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    public void setOperatingSystem(ServerOperatingSystem operatingSystem) {
        this.operatingSystem = operatingSystem;
    }

    public ServerTechnology getTechnology() {
        return technology;
    }

    public void setTechnology(ServerTechnology technology) {
        this.technology = technology;
    }

    public EnvironmentType getEnvironment() {
        return environment;
    }

    public void setEnvironment(EnvironmentType environment) {
        this.environment = environment;
    }

    public MidServer getMidServer() {
        return midServer;
    }

    public void setMidServer(MidServer midServer) {
        this.midServer = midServer;
    }

    public ServerStatus getStatus() {
        return status;
    }

    public void setStatus(ServerStatus status) {
        this.status = status;
    }
}
