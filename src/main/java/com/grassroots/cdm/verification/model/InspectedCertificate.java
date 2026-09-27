package com.grassroots.cdm.verification.model;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Detailed domain model of an X.509 certificate presented by a live TLS endpoint.
 */
public record InspectedCertificate(
        X509Certificate x509Certificate,
        String sha256Fingerprint,
        String sha1Fingerprint,
        String serialNumberHex,
        String serialNumberDecimal,
        String commonName,
        String subjectDn,
        List<String> subjectAlternativeNames,
        List<String> dnsNames,
        List<String> ipAddresses,
        String issuerDn,
        String issuerCommonName,
        Instant notBefore,
        Instant notAfter,
        String signatureAlgorithm,
        int version
) {
    public InspectedCertificate {
        subjectAlternativeNames = subjectAlternativeNames != null ? Collections.unmodifiableList(subjectAlternativeNames) : List.of();
        dnsNames = dnsNames != null ? Collections.unmodifiableList(dnsNames) : List.of();
        ipAddresses = ipAddresses != null ? Collections.unmodifiableList(ipAddresses) : List.of();
    }

    /**
     * Checks whether the presented certificate has expired based on current wall-clock time.
     */
    public boolean isExpired() {
        return notAfter != null && Instant.now().isAfter(notAfter);
    }

    /**
     * Checks whether the presented certificate is not yet valid (premature).
     */
    public boolean isNotYetValid() {
        return notBefore != null && Instant.now().isBefore(notBefore);
    }

    /**
     * Checks whether the presented certificate is currently within its validity window.
     */
    public boolean isValidNow() {
        return !isExpired() && !isNotYetValid();
    }

    /**
     * Validates whether this certificate covers the target host, taking into account
     * Subject Alternative Names (DNS & IP), Common Name fallback, and RFC 6125 wildcard rules.
     *
     * @param targetHost Hostname or IP address of the target endpoint
     * @param allowWildcard Whether wildcard patterns (e.g. *.example.com) should be accepted
     * @return true if the certificate covers targetHost
     */
    public boolean matchesHostname(String targetHost, boolean allowWildcard) {
        if (targetHost == null || targetHost.isBlank()) {
            return false;
        }
        String cleanHost = targetHost.trim().toLowerCase();

        // 1. IP address check
        if (isIpAddress(cleanHost)) {
            for (String ip : ipAddresses) {
                if (cleanHost.equalsIgnoreCase(ip.trim())) {
                    return true;
                }
            }
            if (commonName != null && cleanHost.equalsIgnoreCase(commonName.trim())) {
                return true;
            }
            return false;
        }

        // 2. DNS SAN check (RFC 6125: if SANs present, they take precedence)
        if (!dnsNames.isEmpty()) {
            for (String pattern : dnsNames) {
                if (matchesDomainPattern(cleanHost, pattern.trim().toLowerCase(), allowWildcard)) {
                    return true;
                }
            }
            return false;
        }

        // 3. Fallback to Common Name if no DNS SANs present
        if (commonName != null && !commonName.isBlank()) {
            return matchesDomainPattern(cleanHost, commonName.trim().toLowerCase(), allowWildcard);
        }

        return false;
    }

    private static boolean matchesDomainPattern(String host, String pattern, boolean allowWildcard) {
        if (pattern.equalsIgnoreCase(host)) {
            return true;
        }
        if (!allowWildcard || !pattern.startsWith("*.")) {
            return false;
        }
        // RFC 6125: Wildcard only allowed in left-most label
        String suffix = pattern.substring(1); // e.g. ".example.com"
        if (!host.endsWith(suffix) || host.length() <= suffix.length()) {
            return false;
        }
        String prefix = host.substring(0, host.length() - suffix.length());
        return !prefix.isEmpty() && !prefix.contains(".");
    }

    private static boolean isIpAddress(String host) {
        return host.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$") || host.contains(":");
    }

    /**
     * Checks if the presented certificate matches expected SHA-256 or SHA-1 fingerprint.
     * Handles case-insensitive comparisons, colons, dashes, and whitespace normalization.
     */
    public boolean matchesFingerprint(String expected) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        String clean = normalizeHex(expected);
        return clean.equalsIgnoreCase(normalizeHex(sha256Fingerprint))
                || clean.equalsIgnoreCase(normalizeHex(sha1Fingerprint));
    }

    /**
     * Checks if the presented serial number matches expected serial number (hexadecimal or decimal).
     */
    public boolean matchesSerialNumber(String expected) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        String clean = expected.trim();
        if (clean.startsWith("0x") || clean.startsWith("0X")) {
            clean = clean.substring(2);
        }
        String cleanHex = normalizeHex(clean);
        if (normalizeHex(serialNumberHex).equalsIgnoreCase(cleanHex)) {
            return true;
        }
        if (serialNumberDecimal != null && serialNumberDecimal.equals(clean)) {
            return true;
        }
        // BigInteger decimal/hex comparison
        try {
            BigInteger expectedBigInt = cleanHex.length() > 0 ? new BigInteger(cleanHex, 16) : new BigInteger(clean);
            if (serialNumberDecimal != null) {
                BigInteger actualBigInt = new BigInteger(serialNumberDecimal);
                return expectedBigInt.equals(actualBigInt);
            }
        } catch (Exception ignored) {
            // Non-numeric or non-hex string
        }
        return false;
    }

    /**
     * Checks if the presented issuer matches expected issuer (case-insensitive substring or DN match).
     */
    public boolean matchesIssuer(String expectedIssuer) {
        if (expectedIssuer == null || expectedIssuer.isBlank()) {
            return false;
        }
        String cleanExpected = expectedIssuer.trim().toLowerCase();
        if (issuerDn != null && issuerDn.toLowerCase().contains(cleanExpected)) {
            return true;
        }
        if (issuerCommonName != null && issuerCommonName.toLowerCase().contains(cleanExpected)) {
            return true;
        }
        return false;
    }

    private static String normalizeHex(String hex) {
        if (hex == null) return "";
        return hex.replaceAll("[:\\s\\-]", "").toUpperCase();
    }
}
