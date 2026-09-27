package com.grassroots.cdm.integration.midserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Certificate reference contract passed to the MID Server.
 * Strictly holds identity references and vault locators; NEVER contains plaintext private keys.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CertificateReferenceDto {

    private UUID certificateId;
    private String serialNumber;
    private String thumbprint;
    private String commonName;
    private List<String> subjectAlternativeNames = Collections.emptyList();
    private String vaultSecretReference;
    private Instant validityNotAfter;

    public CertificateReferenceDto() {
    }

    public CertificateReferenceDto(UUID certificateId, String serialNumber, String thumbprint,
                                   String commonName, List<String> subjectAlternativeNames,
                                   String vaultSecretReference, Instant validityNotAfter) {
        this.certificateId = certificateId;
        this.serialNumber = serialNumber;
        this.thumbprint = thumbprint;
        this.commonName = commonName;
        this.subjectAlternativeNames = subjectAlternativeNames != null ? subjectAlternativeNames : Collections.emptyList();
        this.vaultSecretReference = vaultSecretReference;
        this.validityNotAfter = validityNotAfter;
    }

    public UUID getCertificateId() {
        return certificateId;
    }

    public void setCertificateId(UUID certificateId) {
        this.certificateId = certificateId;
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
        this.subjectAlternativeNames = subjectAlternativeNames;
    }

    public String getVaultSecretReference() {
        return vaultSecretReference;
    }

    public void setVaultSecretReference(String vaultSecretReference) {
        this.vaultSecretReference = vaultSecretReference;
    }

    public Instant getValidityNotAfter() {
        return validityNotAfter;
    }

    public void setValidityNotAfter(Instant validityNotAfter) {
        this.validityNotAfter = validityNotAfter;
    }

    @Override
    public String toString() {
        // Redact vault secret locator and ensure zero risk of credential leakage
        return "CertificateReferenceDto{" +
                "certificateId=" + certificateId +
                ", serialNumber='" + serialNumber + '\'' +
                ", thumbprint='" + thumbprint + '\'' +
                ", commonName='" + commonName + '\'' +
                ", subjectAlternativeNames=" + subjectAlternativeNames +
                ", vaultSecretReference='[REDACTED]'" +
                ", validityNotAfter=" + validityNotAfter +
                '}';
    }
}
