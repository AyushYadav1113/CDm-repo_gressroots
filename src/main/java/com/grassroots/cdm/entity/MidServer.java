package com.grassroots.cdm.entity;

import com.grassroots.cdm.entity.enums.MidServerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entity representing ServiceNow MID Server execution agent nodes.
 */
@Entity
@Table(name = "mid_servers")
public class MidServer extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private MidServerStatus status = MidServerStatus.UP;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "network_metadata", columnDefinition = "JSONB")
    private String networkMetadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "health_info", columnDefinition = "JSONB")
    private String healthInfo;

    public MidServer() {
    }

    public MidServer(String name, String endpoint, MidServerStatus status) {
        this.name = name;
        this.endpoint = endpoint;
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public MidServerStatus getStatus() {
        return status;
    }

    public void setStatus(MidServerStatus status) {
        this.status = status;
    }

    public String getNetworkMetadata() {
        return networkMetadata;
    }

    public void setNetworkMetadata(String networkMetadata) {
        this.networkMetadata = networkMetadata;
    }

    public String getHealthInfo() {
        return healthInfo;
    }

    public void setHealthInfo(String healthInfo) {
        this.healthInfo = healthInfo;
    }
}
