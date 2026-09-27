package com.grassroots.cdm.integration;

import java.util.Arrays;

/**
 * Transient holder for target credentials retrieved on-demand from CyberArk.
 * Strictly in-memory; credentials must never be written to disk, database, or logs.
 * Implements {@link AutoCloseable} to guarantee immediate password wiping upon completion.
 */
public record CredentialSecret(
        String username,
        char[] password,
        String privateKeyPassphrase,
        String targetHost
) implements AutoCloseable {

    /**
     * Clear sensitive memory buffers immediately after deployment execution.
     */
    public void wipe() {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * AutoCloseable contract ensures automatic wiping when used in try-with-resources.
     */
    @Override
    public void close() {
        wipe();
    }

    /**
     * Security invariant: strictly redacts secret materials from string representations
     * to eliminate risk of accidental credential leakage into log files.
     */
    @Override
    public String toString() {
        return "CredentialSecret[username=" + username + ", targetHost=" + targetHost
                + ", password=[REDACTED], privateKeyPassphrase="
                + (privateKeyPassphrase != null ? "[REDACTED]" : "null") + "]";
    }
}
