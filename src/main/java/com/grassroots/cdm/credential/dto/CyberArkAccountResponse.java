package com.grassroots.cdm.credential.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

/**
 * Response model mapping CyberArk Central Credential Provider (CCP) /AIMWebService/api/Accounts JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CyberArkAccountResponse {

    @JsonProperty("Content")
    private char[] content;

    @JsonProperty("UserName")
    private String userName;

    @JsonProperty("Address")
    private String address;

    @JsonProperty("Safe")
    private String safe;

    @JsonProperty("Folder")
    private String folder;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("PolicyID")
    private String policyId;

    @JsonProperty("DeviceType")
    private String deviceType;

    @JsonProperty("PasswordChangeInProcess")
    private boolean passwordChangeInProcess;

    @JsonProperty("LastSuccessChange")
    private String lastSuccessChange;

    @JsonProperty("LastTask")
    private String lastTask;

    @JsonProperty("LastTaskExecutionDate")
    private String lastTaskExecutionDate;

    @JsonProperty("PrivateKeyPassphrase")
    private String privateKeyPassphrase;

    @JsonProperty("IsExpired")
    private boolean expired;

    public CyberArkAccountResponse() {
    }

    public char[] getContent() {
        return content;
    }

    public void setContent(char[] content) {
        this.content = content;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getSafe() {
        return safe;
    }

    public void setSafe(String safe) {
        this.safe = safe;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public boolean isPasswordChangeInProcess() {
        return passwordChangeInProcess;
    }

    public void setPasswordChangeInProcess(boolean passwordChangeInProcess) {
        this.passwordChangeInProcess = passwordChangeInProcess;
    }

    public String getLastSuccessChange() {
        return lastSuccessChange;
    }

    public void setLastSuccessChange(String lastSuccessChange) {
        this.lastSuccessChange = lastSuccessChange;
    }

    public String getLastTask() {
        return lastTask;
    }

    public void setLastTask(String lastTask) {
        this.lastTask = lastTask;
    }

    public String getLastTaskExecutionDate() {
        return lastTaskExecutionDate;
    }

    public void setLastTaskExecutionDate(String lastTaskExecutionDate) {
        this.lastTaskExecutionDate = lastTaskExecutionDate;
    }

    public String getPrivateKeyPassphrase() {
        return privateKeyPassphrase;
    }

    public void setPrivateKeyPassphrase(String privateKeyPassphrase) {
        this.privateKeyPassphrase = privateKeyPassphrase;
    }

    public boolean isExpired() {
        return expired;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }

    /**
     * Zeros in-memory password buffer after translation into CredentialSecret.
     */
    public void wipe() {
        if (content != null) {
            Arrays.fill(content, '\0');
        }
    }

    @Override
    public String toString() {
        return "CyberArkAccountResponse[safe=" + safe + ", name=" + name + ", userName=" + userName
                + ", address=" + address + ", passwordChangeInProcess=" + passwordChangeInProcess
                + ", expired=" + expired + ", content=[REDACTED]]";
    }
}
