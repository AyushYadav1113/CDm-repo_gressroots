package com.grassroots.cdm.deployment.planner.generator;

import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Deterministic generator for deployment job idempotency keys.
 * Ensures identical certificate replacement + installation combinations yield the exact
 * same key, preventing duplicate orchestrations across retries and restarts.
 */
public final class IdempotencyKeyGenerator {

    private IdempotencyKeyGenerator() {
    }

    /**
     * Generates a deterministic idempotency key with length <= 128 characters.
     *
     * @param oldCert      the expiring certificate
     * @param newCert      the candidate replacement certificate
     * @param installation the installation target
     * @return deterministic idempotency key string
     */
    public static String generateKey(CertificateRecord oldCert, CertificateRecord newCert, CertificateInstallation installation) {
        Objects.requireNonNull(oldCert, "oldCertificate must not be null");
        Objects.requireNonNull(newCert, "newCertificate must not be null");
        Objects.requireNonNull(installation, "installation must not be null");

        String serverHost = (installation.getServer() != null && installation.getServer().getHostname() != null)
                ? installation.getServer().getHostname().trim().toLowerCase()
                : "server";

        int port = installation.getPort();
        String binding = installation.getBindingInfo() != null ? installation.getBindingInfo().trim() : "default";

        String rawContent = String.format("%s|%s|%s|%s|%d|%s",
                oldCert.getId(),
                newCert.getId(),
                installation.getId(),
                serverHost,
                port,
                binding
        );

        String hash = hashSha256(rawContent).substring(0, 16).toUpperCase();
        String prefix = String.format("DEP:%s:%d:", serverHost, port);

        // Enforce maximum length of 128 characters
        if (prefix.length() + hash.length() > 128) {
            prefix = prefix.substring(0, 128 - hash.length());
        }

        return prefix + hash;
    }

    private static String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
