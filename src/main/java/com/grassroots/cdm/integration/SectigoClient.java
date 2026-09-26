package com.grassroots.cdm.integration;

import java.util.List;

/**
 * Interface contract for Sectigo Certificate Manager (SCM) REST API integration.
 */
public interface SectigoClient {

    /**
     * Retrieves newly issued or renewed certificates ready for deployment.
     *
     * @return list of renewed certificate metadata
     */
    List<SectigoCertificateMetadata> fetchRenewedCertificates();

    /**
     * Downloads full certificate chain and PEM bundle from Sectigo.
     *
     * @param certificateId Sectigo certificate ID
     * @return certificate chain bundle in PEM format
     */
    byte[] downloadCertificateChain(String certificateId);

    record SectigoCertificateMetadata(
            String certificateId,
            String commonName,
            String serialNumber,
            String sha256Fingerprint,
            String expiresDate
    ) {}
}
