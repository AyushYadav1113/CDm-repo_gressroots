package com.grassroots.cdm.integration.sectigo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw DTO representing a certificate record as returned by Sectigo SCM REST API.
 * Uses lenient aliases and fallback deserialization for maximum resilience.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SectigoCertificateDto {

    @JsonAlias({"certificateId", "sslId", "certId"})
    private String id;

    @JsonAlias({"cn", "name"})
    private String commonName;

    @JsonAlias({"subjectAltNames", "sans", "san"})
    @JsonDeserialize(using = StringListOrStringDeserializer.class)
    private List<String> subjectAlternativeNames = new ArrayList<>();

    @JsonAlias({"serial"})
    private String serialNumber;

    @JsonAlias({"thumbprint", "fingerprint", "fingerprintSha256"})
    private String sha256Fingerprint;

    @JsonAlias({"ca", "issuerName"})
    private String issuer;

    @JsonAlias({"notBefore", "issuedDate", "valid_from", "effectiveDate"})
    private String validFrom;

    @JsonAlias({"notAfter", "expires", "valid_to", "expirationDate"})
    private String validTo;

    @JsonAlias({"state", "orderStatus", "lifecycleStatus"})
    private String status;

    @JsonAlias({"order_id", "externalOrderId"})
    private String orderId;

    @JsonAlias({"renewedFromId", "replacedCertificateId", "renewedFrom"})
    private String renewedFromCertificateId;

    private String keyAlgorithm;
    private String signatureAlgorithm;

    public SectigoCertificateDto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public List<String> getSubjectAlternativeNames() {
        return subjectAlternativeNames;
    }

    public void setSubjectAlternativeNames(List<String> subjectAlternativeNames) {
        this.subjectAlternativeNames = subjectAlternativeNames != null ? subjectAlternativeNames : new ArrayList<>();
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getSha256Fingerprint() {
        return sha256Fingerprint;
    }

    public void setSha256Fingerprint(String sha256Fingerprint) {
        this.sha256Fingerprint = sha256Fingerprint;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getRenewedFromCertificateId() {
        return renewedFromCertificateId;
    }

    public void setRenewedFromCertificateId(String renewedFromCertificateId) {
        this.renewedFromCertificateId = renewedFromCertificateId;
    }

    public String getKeyAlgorithm() {
        return keyAlgorithm;
    }

    public void setKeyAlgorithm(String keyAlgorithm) {
        this.keyAlgorithm = keyAlgorithm;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    @Override
    public String toString() {
        return "SectigoCertificateDto{" +
                "id='" + id + '\'' +
                ", commonName='" + commonName + '\'' +
                ", serialNumber='" + serialNumber + '\'' +
                ", status='" + status + '\'' +
                ", validTo='" + validTo + '\'' +
                '}';
    }
}
