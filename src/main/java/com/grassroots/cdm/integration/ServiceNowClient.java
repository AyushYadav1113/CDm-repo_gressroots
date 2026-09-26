package com.grassroots.cdm.integration;

import java.util.List;

/**
 * Interface contract for ServiceNow Discovery CMDB certificate synchronization.
 */
public interface ServiceNowClient {

    /**
     * Discovers active certificates recorded in ServiceNow CMDB (cmdb_ci_certificate).
     *
     * @return list of certificate metadata records from ServiceNow
     */
    List<DiscoveredCertificateRecord> fetchDiscoveredCertificates();

    /**
     * Updates certificate discovery state in ServiceNow after deployment verification.
     *
     * @param externalSysId ServiceNow sys_id
     * @param status Updated operational status
     */
    void updateCertificateStatus(String externalSysId, String status);

    record DiscoveredCertificateRecord(
            String sysId,
            String commonName,
            String serialNumber,
            String issuer,
            String validTo,
            String targetHost
    ) {}
}
