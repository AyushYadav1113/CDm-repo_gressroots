package com.grassroots.cdm.deployment.adapter.java.profile;

/**
 * Supported Java KeyStore format types.
 */
public enum KeystoreType {
    JKS("JKS", ".jks"),
    PKCS12("PKCS12", ".p12");

    private final String format;
    private final String defaultExtension;

    KeystoreType(String format, String defaultExtension) {
        this.format = format;
        this.defaultExtension = defaultExtension;
    }

    public String getFormat() {
        return format;
    }

    public String getDefaultExtension() {
        return defaultExtension;
    }

    public static KeystoreType fromLocation(String location) {
        if (location == null) {
            return PKCS12;
        }
        String lower = location.toLowerCase();
        if (lower.endsWith(".jks") || lower.endsWith(".keystore")) {
            return JKS;
        }
        return PKCS12;
    }

    public static KeystoreType fromString(String type) {
        if (type == null || type.isBlank()) {
            return PKCS12;
        }
        if ("JKS".equalsIgnoreCase(type.trim())) {
            return JKS;
        }
        return PKCS12;
    }
}
