package com.grassroots.cdm.entity;

import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entity representing discovered or renewed SSL/TLS X.509 certificate metadata.
 */
@Entity
@Table(name = "certificates")
public class CertificateRecord extends BaseEntity {

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "serial_number", nullable = false, length = 128)
    private String serialNumber;

    @Column(name = "thumbprint", nullable = false, unique = true, length = 128)
    private String thumbprint;

    @Column(name = "fingerprint_sha256", nullable = false, unique = true, length = 128)
    private String fingerprintSha256;

    @Column(name = "common_name", nullable = false)
    private String commonName;

    @Column(name = "subject_alternative_names", columnDefinition = "TEXT")
    private String subjectAlternativeNames;

    @Column(name = "issuer")
    private String issuer;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 50)
    private CertificateSource source = CertificateSource.SERVICENOW;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private CertificateStatus status = CertificateStatus.ACTIVE;

    public CertificateRecord() {
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getThumbprint() {
        return thumbprint;
    }

    public void setThumbprint(String thumbprint) {
        this.thumbprint = thumbprint;
        if (this.fingerprintSha256 == null) {
            this.fingerprintSha256 = thumbprint;
        }
    }

    public String getFingerprintSha256() {
        return fingerprintSha256;
    }

    public void setFingerprintSha256(String fingerprintSha256) {
        this.fingerprintSha256 = fingerprintSha256;
        if (this.thumbprint == null) {
            this.thumbprint = fingerprintSha256;
        }
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

    public CertificateSource getSource() {
        return source;
    }

    public void setSource(CertificateSource source) {
        this.source = source;
    }

    public CertificateStatus getStatus() {
        return status;
    }

    public void setStatus(CertificateStatus status) {
        this.status = status;
    }
}
