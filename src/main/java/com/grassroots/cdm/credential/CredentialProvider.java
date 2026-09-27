package com.grassroots.cdm.credential;

import com.grassroots.cdm.credential.dto.CredentialRequest;
import com.grassroots.cdm.integration.CredentialSecret;

/**
 * Core abstraction for retrieving transient deployment credentials on-demand.
 * Enforces zero persistent storage of secrets and minimum in-memory residence.
 */
public interface CredentialProvider {

    /**
     * Retrieves credentials on-demand according to the specified request.
     *
     * @param request Structured credential request parameters
     * @return Transient CredentialSecret (AutoCloseable, memory wiping supported)
     */
    CredentialSecret getCredential(CredentialRequest request);

    /**
     * Convenience method to retrieve credentials by safe, account name, and optional target host.
     *
     * @param safeName CyberArk safe identifier
     * @param accountName Target account object name
     * @param targetHost Optional target host FQDN / IP
     * @return Transient CredentialSecret
     */
    default CredentialSecret getCredential(String safeName, String accountName, String targetHost) {
        return getCredential(CredentialRequest.builder()
                .safeName(safeName)
                .accountName(accountName)
                .targetHost(targetHost)
                .build());
    }

    /**
     * Retrieves credentials via canonical URI locator (e.g. cyberark://SafeName/AccountName).
     *
     * @param secretLocator Canonical URI secret locator
     * @return Transient CredentialSecret
     */
    CredentialSecret getCredentialByLocator(String secretLocator);

    /**
     * Functional execution pattern: acquires credential, runs callback, and immediately
     * wipes credentials from memory via try-with-resources.
     *
     * @param request Credential request
     * @param action Execution callback receiving the secret
     * @param <T> Return type
     * @return Result of the action callback
     * @throws Exception Any error thrown by the action
     */
    default <T> T executeWithCredential(CredentialRequest request, CredentialAction<T> action) throws Exception {
        try (CredentialSecret secret = getCredential(request)) {
            return action.execute(secret);
        }
    }

    /**
     * Checks if the credential provider is online and available.
     *
     * @return true if vault health probe succeeds
     */
    boolean isAvailable();
}
