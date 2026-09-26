package com.grassroots.cdm.integration.servicenow;

import com.grassroots.cdm.integration.servicenow.dto.ServiceNowCertificateDto;

import java.util.List;
import java.util.Optional;

/**
 * Isolated HTTP client interface for interacting with the ServiceNow Table API.
 * All implementations handle HTTP transport, authentication, retries, and deserialization.
 */
public interface ServiceNowClient {

    /**
     * Queries certificate records from the cmdb_ci_certificate table with pagination.
     *
     * @param queryFilter   ServiceNow sysparm_query expression (e.g. active=true)
     * @param limit         Maximum records to retrieve
     * @param offset        Page offset
     * @param correlationId Distributed trace correlation ID
     * @return List of raw certificate DTOs
     */
    List<ServiceNowCertificateDto> fetchCertificates(String queryFilter, int limit, int offset, String correlationId);

    /**
     * Fetches all active certificate records from ServiceNow, automatically paging through results.
     *
     * @param correlationId Distributed trace correlation ID
     * @return Complete list of certificate DTOs
     */
    List<ServiceNowCertificateDto> fetchAllCertificates(String correlationId);

    /**
     * Fetches a single certificate by sys_id.
     *
     * @param sysId         ServiceNow sys_id
     * @param correlationId Distributed trace correlation ID
     * @return Optional certificate DTO
     */
    Optional<ServiceNowCertificateDto> fetchCertificateBySysId(String sysId, String correlationId);

    /**
     * Updates certificate operational state or verification status in ServiceNow.
     *
     * @param sysId         ServiceNow sys_id
     * @param status        New status value
     * @param correlationId Distributed trace correlation ID
     */
    void updateCertificateStatus(String sysId, String status, String correlationId);

    default List<ServiceNowCertificateDto> fetchCertificates(String queryFilter, int limit, int offset) {
        return fetchCertificates(queryFilter, limit, offset, null);
    }

    default List<ServiceNowCertificateDto> fetchAllCertificates() {
        return fetchAllCertificates(null);
    }

    default Optional<ServiceNowCertificateDto> fetchCertificateBySysId(String sysId) {
        return fetchCertificateBySysId(sysId, null);
    }

    default void updateCertificateStatus(String sysId, String status) {
        updateCertificateStatus(sysId, status, null);
    }
}
