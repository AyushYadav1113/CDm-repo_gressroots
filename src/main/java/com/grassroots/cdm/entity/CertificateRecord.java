package com.grassroots.cdm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entity representing SSL/TLS certificates discovered from ServiceNow or fetched from Sectigo.
 */
@Entity
@Table(name = "certificates")
public class CertificateRecord extends BaseEntity {

    @Column(name = "serial_number", nullable = false, length = 128)
    private String serialNumber;

    @Column(name = "common_name", nullable = false)
    private String commonName;

    @Column(name = "subject_alternative_names", columnDefinition = "TEXT")
    private String subjectAlternativeNames;

    @Column(name = "issuer")
    private String issuer;

    @Column(name = "fingerprint_sha256", nullable = false, unique = true, length = 128)
    private String fingerprintSha256;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "ACTIVE";

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getSubjectAlternativeNames() {
        return subjectAlternativeNames;
    }

    public void setSubjectAlternativeNames(String subjectAlternativeNames) {
        this.subjectAlternativeNames = subjectAlternativeNames;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getFingerprintSha256() {
        return fingerprintSha256;
    }

    public void setFingerprintSha256(String fingerprintSha256) {
        this.fingerprintSha256 = fingerprintSha256;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public void setValidTo(Instant validTo) {
        this.validTo = validTo;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
