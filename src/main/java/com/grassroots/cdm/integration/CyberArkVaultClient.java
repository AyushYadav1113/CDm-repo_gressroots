package com.grassroots.cdm.integration;

/**
 * Interface contract for CyberArk enterprise vault integration.
 *
 * Retrieves target host deployment credentials just-in-time via CyberArk CCP/AIM.
 * Credentials are wiped from memory immediately after dispatch.
 */
public interface CyberArkVaultClient {

    /**
     * Retrieves credentials for a target host from a designated CyberArk Safe.
     *
     * @param safeName CyberArk safe identifier
     * @param accountName Account object name in safe
     * @param targetHost Target machine FQDN / IP
     * @return transient CredentialSecret object
     */
    CredentialSecret retrieveCredential(String safeName, String accountName, String targetHost);
}
