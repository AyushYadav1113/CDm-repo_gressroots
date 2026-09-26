package com.grassroots.cdm.integration.servicenow;

import com.grassroots.cdm.integration.servicenow.model.DiscoveredCertificateItem;

import java.util.List;
import java.util.Optional;

/**
 * High-level integration service that isolates the domain layer from ServiceNow HTTP transports
 * and ServiceNow-specific Table API DTO structures.
 */
public interface ServiceNowIntegrationService {

    /**
     * Retrieves all discovered active certificates, validated and transformed into clean domain models.
     *
     * @param correlationId Distributed trace correlation ID
     * @return List of clean DiscoveredCertificateItem instances
     */
    List<DiscoveredCertificateItem> fetchActiveCertificates(String correlationId);

    /**
     * Retrieves a single certificate by ServiceNow sys_id.
     *
     * @param sysId         ServiceNow sys_id
     * @param correlationId Distributed trace correlation ID
     * @return Optional DiscoveredCertificateItem
     */
    Optional<DiscoveredCertificateItem> fetchCertificateBySysId(String sysId, String correlationId);

    /**
     * Updates certificate verification or deployment state in ServiceNow.
     *
     * @param sysId         ServiceNow sys_id
     * @param status        New status
     * @param correlationId Distributed trace correlation ID
     */
    void updateCertificateStatus(String sysId, String status, String correlationId);

    default List<DiscoveredCertificateItem> fetchActiveCertificates() {
        return fetchActiveCertificates(null);
    }
}
