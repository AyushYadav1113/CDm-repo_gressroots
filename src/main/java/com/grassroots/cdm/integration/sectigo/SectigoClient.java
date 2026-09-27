package com.grassroots.cdm.integration.sectigo;

import com.grassroots.cdm.integration.sectigo.dto.SectigoCertificateDto;

import java.util.List;
import java.util.Optional;

/**
 * Isolated HTTP client interface for interacting with the Sectigo Certificate Manager (SCM) REST API.
 * Encapsulates HTTP transport, custom header authentication, retries, pagination, and error translation.
 */
public interface SectigoClient {

    /**
     * Retrieves a page of certificates matching status and pagination criteria.
     *
     * @param size          Page size (maximum records per request)
     * @param position      Starting record offset (0-indexed)
     * @param status        Optional lifecycle filter (e.g. "ISSUED", "ACTIVE", "ALL")
     * @param correlationId Distributed trace correlation ID
     * @return List of raw Sectigo certificate DTOs
     */
    List<SectigoCertificateDto> fetchCertificates(int size, int position, String status, String correlationId);

    /**
     * Fetches all certificates matching status filter, automatically paginating until completion.
     *
     * @param status        Lifecycle status filter (e.g. "ISSUED")
     * @param correlationId Distributed trace correlation ID
     * @return Complete list of raw Sectigo certificate DTOs
     */
    List<SectigoCertificateDto> fetchAllCertificates(String status, String correlationId);

    /**
     * Fetches detailed metadata for a single certificate by its Sectigo certificate ID.
     *
     * @param certificateId Sectigo certificate ID
     * @param correlationId Distributed trace correlation ID
     * @return Optional certificate DTO
     */
    Optional<SectigoCertificateDto> fetchCertificateById(String certificateId, String correlationId);

    /**
     * Downloads the public X.509 certificate chain bundle (PEM or DER format).
     * Strictly retrieves public certificate chains; never requests private keys.
     *
     * @param certificateId Sectigo certificate ID
     * @param correlationId Distributed trace correlation ID
     * @return Raw certificate chain bytes
     */
    byte[] downloadCertificateChain(String certificateId, String correlationId);

    default List<SectigoCertificateDto> fetchCertificates(int size, int position, String status) {
        return fetchCertificates(size, position, status, null);
    }

    default List<SectigoCertificateDto> fetchAllCertificates(String status) {
        return fetchAllCertificates(status, null);
    }

    default Optional<SectigoCertificateDto> fetchCertificateById(String certificateId) {
        return fetchCertificateById(certificateId, null);
    }

    default byte[] downloadCertificateChain(String certificateId) {
        return downloadCertificateChain(certificateId, null);
    }
}
