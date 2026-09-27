package com.grassroots.cdm.deployment.planner.generator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Generator for human-readable, trackable deployment job references.
 */
public final class JobReferenceGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private JobReferenceGenerator() {
    }

    /**
     * Generates a deterministic job reference based on the idempotency key and date.
     *
     * @param idempotencyKey the idempotency key
     * @return reference string like "JOB-20260927-A1B2C3D4"
     */
    public static String generateReference(String idempotencyKey) {
        String dateStr = LocalDate.now().format(DATE_FORMATTER);
        String shortHash = hashSha256(idempotencyKey).substring(0, 8).toUpperCase();
        return String.format("JOB-%s-%s", dateStr, shortHash);
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
