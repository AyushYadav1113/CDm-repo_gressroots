package com.grassroots.cdm.integration.midserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Health telemetry returned by MID Server ping endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MidServerHealthDto {

    private String status = "UP";
    private String version;
    private int activeTasks = 0;
    private double systemCpuLoad = 0.0;
    private Map<String, Object> details;

    public MidServerHealthDto() {
    }

    public MidServerHealthDto(String status, String version, int activeTasks) {
        this.status = status;
        this.version = version;
        this.activeTasks = activeTasks;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getActiveTasks() {
        return activeTasks;
    }

    public void setActiveTasks(int activeTasks) {
        this.activeTasks = activeTasks;
    }

    public double getSystemCpuLoad() {
        return systemCpuLoad;
    }

    public void setSystemCpuLoad(double systemCpuLoad) {
        this.systemCpuLoad = systemCpuLoad;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}
