package com.grassroots.cdm.integration.servicenow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw data transfer object representing a certificate record returned by the ServiceNow Table API (cmdb_ci_certificate).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceNowCertificateDto {

    @JsonProperty("sys_id")
    private String sysId;

    @JsonProperty("common_name")
    private String commonName;

    @JsonProperty("name")
    private String name;

    @JsonProperty("serial_number")
    private String serialNumber;

    @JsonProperty("thumbprint")
    private String thumbprint;

    @JsonProperty("fingerprint")
    private String fingerprint;

    @JsonProperty("issuer")
    private String issuer;

    @JsonProperty("valid_from")
    private String validFrom;

    @JsonProperty("valid_to")
    private String validTo;

    @JsonProperty("subject_alternative_names")
    private String subjectAlternativeNames;

    @JsonProperty("sans")
    private String sans;

    @JsonProperty("operational_status")
    private String operationalStatus;

    @JsonProperty("state")
    private String state;

    @JsonProperty("target_host")
    private String targetHost;

    @JsonProperty("fqdn")
    private String fqdn;

    @JsonProperty("ip_address")
    private String ipAddress;

    @JsonProperty("port")
    private String port;

    @JsonProperty("technology")
    private String technology;

    @JsonProperty("sys_updated_on")
    private String sysUpdatedOn;

    public ServiceNowCertificateDto() {
    }

    public String getSysId() {
        return sysId;
    }

    public void setSysId(String sysId) {
        this.sysId = sysId;
    }

    public String getCommonName() {
        return (commonName != null && !commonName.isBlank()) ? commonName : name;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getThumbprint() {
        return (thumbprint != null && !thumbprint.isBlank()) ? thumbprint : fingerprint;
    }

    public void setThumbprint(String thumbprint) {
        this.thumbprint = thumbprint;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(String validFrom) {
        this.validFrom = validFrom;
    }

    public String getValidTo() {
        return validTo;
    }

    public void setValidTo(String validTo) {
        this.validTo = validTo;
    }

    public String getSubjectAlternativeNames() {
        return (subjectAlternativeNames != null && !subjectAlternativeNames.isBlank())
                ? subjectAlternativeNames : sans;
    }

    public void setSubjectAlternativeNames(String subjectAlternativeNames) {
        this.subjectAlternativeNames = subjectAlternativeNames;
    }

    public String getSans() {
        return sans;
    }

    public void setSans(String sans) {
        this.sans = sans;
    }

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(String operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTargetHost() {
        return (targetHost != null && !targetHost.isBlank()) ? targetHost : fqdn;
    }

    public void setTargetHost(String targetHost) {
        this.targetHost = targetHost;
    }

    public String getFqdn() {
        return fqdn;
    }

    public void setFqdn(String fqdn) {
        this.fqdn = fqdn;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getTechnology() {
        return technology;
    }

    public void setTechnology(String technology) {
        this.technology = technology;
    }

    public String getSysUpdatedOn() {
        return sysUpdatedOn;
    }

    public void setSysUpdatedOn(String sysUpdatedOn) {
        this.sysUpdatedOn = sysUpdatedOn;
    }

    @Override
    public String toString() {
        return "ServiceNowCertificateDto{" +
                "sysId='" + sysId + '\'' +
                ", commonName='" + getCommonName() + '\'' +
                ", serialNumber='" + serialNumber + '\'' +
                ", thumbprint='" + getThumbprint() + '\'' +
                ", validTo='" + validTo + '\'' +
                ", targetHost='" + getTargetHost() + '\'' +
                '}';
    }
}
