package com.grassroots.cdm.credential.cyberark;

import com.grassroots.cdm.credential.dto.CyberArkAccountResponse;

/**
 * Interface contract for communicating with CyberArk Central Credential Provider (CCP) / AIM REST API.
 */
public interface CyberArkClient {

    /**
     * Queries CyberArk Central Credential Provider (CCP) for account credentials.
     *
     * @param safe Safe identifier
     * @param account Account object name
     * @param targetHost Target host address or FQDN (optional)
     * @return CyberArkAccountResponse containing secret content and account metadata
     */
    CyberArkAccountResponse getAccount(String safe, String account, String targetHost);

    /**
     * Queries CyberArk Central Credential Provider (CCP) for account credentials without host filtering.
     *
     * @param safe Safe identifier
     * @param account Account object name
     * @return CyberArkAccountResponse
     */
    default CyberArkAccountResponse getAccount(String safe, String account) {
        return getAccount(safe, account, null);
    }

    /**
     * Checks if CyberArk service endpoint is available.
     *
     * @return true if CCP health/ping responds successfully
     */
    boolean ping();
}
