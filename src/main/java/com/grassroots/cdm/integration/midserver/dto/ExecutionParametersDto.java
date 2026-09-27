package com.grassroots.cdm.integration.midserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Execution parameters instructing the MID Server how to install and bind the certificate.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionParametersDto {

    private String installationPath;
    private String bindingAlias;
    private boolean restartService = true;
    private boolean reloadConfig = false;
    private boolean backupExisting = true;
    private int timeoutSeconds = 180;
    private Map<String, String> customSettings = new HashMap<>();

    public ExecutionParametersDto() {
    }

    public ExecutionParametersDto(String installationPath, String bindingAlias, boolean restartService,
                                  boolean reloadConfig, boolean backupExisting, int timeoutSeconds,
                                  Map<String, String> customSettings) {
        this.installationPath = installationPath;
        this.bindingAlias = bindingAlias;
        this.restartService = restartService;
        this.reloadConfig = reloadConfig;
        this.backupExisting = backupExisting;
        this.timeoutSeconds = timeoutSeconds;
        if (customSettings != null) {
            this.customSettings.putAll(customSettings);
        }
    }

    public String getInstallationPath() {
        return installationPath;
    }

    public void setInstallationPath(String installationPath) {
        this.installationPath = installationPath;
    }

    public String getBindingAlias() {
        return bindingAlias;
    }

    public void setBindingAlias(String bindingAlias) {
        this.bindingAlias = bindingAlias;
    }

    public boolean isRestartService() {
        return restartService;
    }

    public void setRestartService(boolean restartService) {
        this.restartService = restartService;
    }

    public boolean isReloadConfig() {
        return reloadConfig;
    }

    public void setReloadConfig(boolean reloadConfig) {
        this.reloadConfig = reloadConfig;
    }

    public boolean isBackupExisting() {
        return backupExisting;
    }

    public void setBackupExisting(boolean backupExisting) {
        this.backupExisting = backupExisting;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Map<String, String> getCustomSettings() {
        return Collections.unmodifiableMap(customSettings);
    }

    public void setCustomSettings(Map<String, String> customSettings) {
        this.customSettings = customSettings != null ? new HashMap<>(customSettings) : new HashMap<>();
    }

    @Override
    public String toString() {
        return "ExecutionParametersDto{" +
                "installationPath='" + installationPath + '\'' +
                ", bindingAlias='" + bindingAlias + '\'' +
                ", restartService=" + restartService +
                ", reloadConfig=" + reloadConfig +
                ", backupExisting=" + backupExisting +
                ", timeoutSeconds=" + timeoutSeconds +
                ", customSettingsCount=" + customSettings.size() +
                '}';
    }
}
