package com.grassroots.cdm.credential;

import com.grassroots.cdm.integration.CredentialSecret;

/**
 * Functional callback interface for executing actions with a transient CredentialSecret.
 *
 * @param <T> Return type of the callback execution
 */
@FunctionalInterface
public interface CredentialAction<T> {

    /**
     * Executes logic using the transient secret. The caller will automatically wipe
     * the secret upon completion.
     *
     * @param secret Active transient secret
     * @return Execution result
     * @throws Exception Any error occurring during execution
     */
    T execute(CredentialSecret secret) throws Exception;
}
