package com.grassroots.cdm.matching.normalizer;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Utility for RFC-compliant DNS name normalization, SAN collection parsing,
 * and wildcard domain matching.
 */
public final class DomainNameNormalizer {

    private DomainNameNormalizer() {
    }

    /**
     * Normalizes a DNS domain name by trimming whitespace, stripping schemes/ports,
     * removing trailing root dots, and lowercasing to canonical form.
     *
     * @param rawDomain Raw domain or hostname
     * @return Canonical normalized domain, or empty string if input is blank
     */
    public static String normalizeDomain(String rawDomain) {
        if (rawDomain == null || rawDomain.isBlank()) {
            return "";
        }

        String domain = rawDomain.trim();

        // Strip common X.509 prefixes like "DNS:" or "CN="
        if (domain.toUpperCase(Locale.ROOT).startsWith("DNS:")) {
            domain = domain.substring(4).trim();
        } else if (domain.toUpperCase(Locale.ROOT).startsWith("CN=")) {
            domain = domain.substring(3).trim();
        }

        // Strip URI scheme if present (e.g. https://api.domain.com)
        int schemeIdx = domain.indexOf("://");
        if (schemeIdx >= 0) {
            domain = domain.substring(schemeIdx + 3).trim();
        }

        // Strip path / query if present
        int slashIdx = domain.indexOf('/');
        if (slashIdx >= 0) {
            domain = domain.substring(0, slashIdx).trim();
        }

        // Strip port if present (avoid IPv6 brackets like [::1]:8080)
        if (!domain.startsWith("[") && domain.contains(":")) {
            int colonIdx = domain.lastIndexOf(':');
            domain = domain.substring(0, colonIdx).trim();
        }

        // Strip trailing root dot (FQDN)
        while (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1).trim();
        }

        return domain.toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a SAN representation (delimited string, JSON array, or single entry)
     * and normalizes every DNS entry into a sorted, deduplicated set.
     *
     * @param sansString Raw SAN string
     * @param fallbackCn Fallback Common Name if SAN string is null/empty
     * @return Sorted set of normalized domain names
     */
    public static Set<String> parseAndNormalizeSans(String sansString, String fallbackCn) {
        Set<String> result = new TreeSet<>();

        if (sansString != null && !sansString.isBlank()) {
            // Strip JSON array brackets if present
            String cleaned = sansString.trim();
            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }

            // Split on comma, semicolon, newline, or quotes
            String[] tokens = cleaned.split("[,;\\n\\r\"']+");
            for (String token : tokens) {
                String normalized = normalizeDomain(token);
                if (!normalized.isEmpty()) {
                    result.add(normalized);
                }
            }
        }

        if (result.isEmpty() && fallbackCn != null && !fallbackCn.isBlank()) {
            String normalizedCn = normalizeDomain(fallbackCn);
            if (!normalizedCn.isEmpty()) {
                result.add(normalizedCn);
            }
        }

        return Collections.unmodifiableSet(result);
    }

    /**
     * Checks if a domain string represents a wildcard domain (e.g. *.example.com).
     */
    public static boolean isWildcard(String domain) {
        if (domain == null) {
            return false;
        }
        String norm = domain.trim().toLowerCase(Locale.ROOT);
        return norm.startsWith("*.");
    }

    /**
     * Evaluates whether a host matches a pattern in accordance with RFC 6125.
     * A wildcard (*.example.com) matches single-level subdomains (api.example.com)
     * but does NOT match multi-level subdomains (sub.api.example.com) or apex (example.com).
     */
    public static boolean matchesWildcard(String pattern, String host) {
        String p = normalizeDomain(pattern);
        String h = normalizeDomain(host);

        if (p.isEmpty() || h.isEmpty()) {
            return false;
        }

        // Exact match (including *.domain.com == *.domain.com)
        if (p.equals(h)) {
            return true;
        }

        if (p.startsWith("*.")) {
            String suffix = p.substring(1); // e.g. ".example.com"
            if (h.endsWith(suffix)) {
                String prefix = h.substring(0, h.length() - suffix.length());
                // Wildcard matches exactly one non-empty label (no internal dots)
                return !prefix.isEmpty() && !prefix.contains(".");
            }
        }

        return false;
    }

    /**
     * Checks equivalence between two domain names, with optional wildcard support.
     */
    public static boolean matchesDomain(String d1, String d2, boolean allowWildcard) {
        String n1 = normalizeDomain(d1);
        String n2 = normalizeDomain(d2);

        if (n1.isEmpty() || n2.isEmpty()) {
            return false;
        }

        if (n1.equals(n2)) {
            return true;
        }

        if (allowWildcard) {
            if (isWildcard(n1) && matchesWildcard(n1, n2)) {
                return true;
            }
            if (isWildcard(n2) && matchesWildcard(n2, n1)) {
                return true;
            }
        }

        return false;
    }
}
