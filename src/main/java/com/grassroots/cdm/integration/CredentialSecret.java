package com.grassroots.cdm.integration;

/**
 * Transient holder for target credentials retrieved on-demand from CyberArk.
 * Strictly in-memory; credentials must never be written to disk, database, or logs.
 */
public record CredentialSecret(
        String username,
        char[] password,
        String privateKeyPassphrase,
        String targetHost
) {
    /**
     * Clear sensitive memory buffers after deployment execution.
     */
    public void wipe() {
        if (password != null) {
            java.util.Arrays.fill(password, '\0');
        }
    }
}
