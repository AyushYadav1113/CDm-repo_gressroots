package com.grassroots.cdm.integration.midserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerTechnology;

/**
 * Target server metadata transmitted in the MID Server deployment task contract.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TargetServerInfoDto {

    private String hostname;
    private String ipAddress;
    private ServerOperatingSystem operatingSystem;
    private ServerTechnology technology;
    private Integer targetPort = 443;
    private EnvironmentType environment;

    public TargetServerInfoDto() {
    }

    public TargetServerInfoDto(String hostname, String ipAddress, ServerOperatingSystem operatingSystem,
                               ServerTechnology technology, Integer targetPort, EnvironmentType environment) {
        this.hostname = hostname;
        this.ipAddress = ipAddress;
        this.operatingSystem = operatingSystem;
        this.technology = technology;
        this.targetPort = targetPort != null ? targetPort : 443;
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

    public Integer getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(Integer targetPort) {
        this.targetPort = targetPort;
    }

    public EnvironmentType getEnvironment() {
        return environment;
    }

    public void setEnvironment(EnvironmentType environment) {
        this.environment = environment;
    }

    @Override
    public String toString() {
        return "TargetServerInfoDto{" +
                "hostname='" + hostname + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", operatingSystem=" + operatingSystem +
                ", technology=" + technology +
                ", targetPort=" + targetPort +
                ", environment=" + environment +
                '}';
    }
}
