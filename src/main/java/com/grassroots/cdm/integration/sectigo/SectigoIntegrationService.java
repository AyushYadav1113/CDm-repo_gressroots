package com.grassroots.cdm.integration.sectigo;

import com.grassroots.cdm.dto.SectigoSyncResultDto;
import com.grassroots.cdm.integration.sectigo.model.SectigoCertificateItem;

import java.util.List;
import java.util.Optional;

/**
 * High-level integration service that coordinates retrieving certificates from Sectigo,
 * transforming them into domain models, and orchestrating idempotent synchronization with the CDM repository.
 */
public interface SectigoIntegrationService {

    /**
     * Retrieves all issued/renewed certificates, transformed into clean intermediate items.
     *
     * @param correlationId Distributed trace correlation ID
     * @return List of clean SectigoCertificateItem instances
     */
    List<SectigoCertificateItem> fetchCertificates(String correlationId);

    /**
     * Retrieves metadata for a single certificate by Sectigo certificate ID.
     *
     * @param certificateId Sectigo certificate ID
     * @param correlationId Distributed trace correlation ID
     * @return Optional SectigoCertificateItem
     */
    Optional<SectigoCertificateItem> fetchCertificateById(String certificateId, String correlationId);

    /**
     * Performs end-to-end idempotent synchronization of newly issued and renewed certificates
     * into the CDM repository, recording audit trails and detecting renewals.
     *
     * @param correlationId Distributed trace correlation ID
     * @return Summary execution result
     */
    SectigoSyncResultDto syncCertificates(String correlationId);

    /**
     * Synchronizes a single certificate by its Sectigo certificate ID into the CDM repository.
     *
     * @param certificateId Sectigo certificate ID
     * @param correlationId Distributed trace correlation ID
     * @return Summary execution result
     */
    SectigoSyncResultDto syncCertificateById(String certificateId, String correlationId);

    /**
     * Downloads the public X.509 certificate chain bundle for deployment.
     *
     * @param certificateId Sectigo certificate ID
     * @param correlationId Distributed trace correlation ID
     * @return Raw certificate chain bytes
     */
    byte[] downloadCertificateChain(String certificateId, String correlationId);

    default List<SectigoCertificateItem> fetchCertificates() {
        return fetchCertificates(null);
    }

    default Optional<SectigoCertificateItem> fetchCertificateById(String certificateId) {
        return fetchCertificateById(certificateId, null);
    }

    default SectigoSyncResultDto syncCertificates() {
        return syncCertificates(null);
    }

    default SectigoSyncResultDto syncCertificateById(String certificateId) {
        return syncCertificateById(certificateId, null);
    }

    default byte[] downloadCertificateChain(String certificateId) {
        return downloadCertificateChain(certificateId, null);
    }
}
