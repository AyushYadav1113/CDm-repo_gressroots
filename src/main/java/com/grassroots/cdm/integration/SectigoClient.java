package com.grassroots.cdm.integration;

import java.util.List;

/**
 * Historical interface placeholder.
 * Replaced by {@link com.grassroots.cdm.integration.sectigo.SectigoClient}.
 */
@Deprecated
public interface SectigoClient extends com.grassroots.cdm.integration.sectigo.SectigoClient {

    default List<SectigoCertificateMetadata> fetchRenewedCertificates() {
        return List.of();
    }

    record SectigoCertificateMetadata(
            String certificateId,
            String commonName,
            String serialNumber,
            String sha256Fingerprint,
            String expiresDate
    ) {}
}
