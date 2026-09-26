package com.grassroots.cdm.discovery;

import com.grassroots.cdm.dto.DiscoveryResultDto;

/**
 * Domain service contract managing discovery and synchronization of certificates from external CMDBs.
 */
public interface DiscoveryService {

    /**
     * Executes end-to-end certificate discovery synchronization against ServiceNow.
     * Upserts certificates, handles deduplication, associates installations, and records audit logs.
     *
     * @param correlationId Distributed trace correlation ID
     * @return Execution summary DTO
     */
    DiscoveryResultDto discoverCertificates(String correlationId);

    /**
     * Executes discovery with an auto-generated correlation ID.
     */
    default DiscoveryResultDto discoverCertificates() {
        return discoverCertificates(null);
    }

    /**
     * Synchronizes a single certificate by ServiceNow sys_id.
     *
     * @param sysId         ServiceNow sys_id
     * @param correlationId Distributed trace correlation ID
     * @return Execution summary DTO
     */
    DiscoveryResultDto syncCertificateBySysId(String sysId, String correlationId);
}
