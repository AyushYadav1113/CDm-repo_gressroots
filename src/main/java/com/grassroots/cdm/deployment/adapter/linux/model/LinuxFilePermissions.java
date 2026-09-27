package com.grassroots.cdm.deployment.adapter.linux.model;

import java.util.Objects;
import java.util.Set;

/**
 * Encapsulates restrictive POSIX filesystem permissions for Linux certificate and key storage.
 * Enforces security rules: private keys must never be world-readable or world-writable.
 */
public record LinuxFilePermissions(
        String fileMode,
        String owner,
        String group
) {
    private static final Set<String> ALLOWED_CERT_MODES = Set.of("0644", "0640", "0600");
    private static final Set<String> ALLOWED_KEY_MODES = Set.of("0600", "0640");

    public LinuxFilePermissions {
        Objects.requireNonNull(fileMode, "fileMode cannot be null");
        Objects.requireNonNull(owner, "owner cannot be null");
        Objects.requireNonNull(group, "group cannot be null");
    }

    public static LinuxFilePermissions defaultCertificatePermissions() {
        return new LinuxFilePermissions("0644", "root", "root");
    }

    public static LinuxFilePermissions defaultPrivateKeyPermissions(String serviceGroup) {
        String grp = (serviceGroup != null && !serviceGroup.isBlank()) ? serviceGroup : "root";
        String mode = "root".equalsIgnoreCase(grp) ? "0600" : "0640";
        return new LinuxFilePermissions(mode, "root", grp);
    }

    public boolean isSecureForPrivateKey() {
        return ALLOWED_KEY_MODES.contains(fileMode);
    }

    public boolean isSecureForCertificate() {
        return ALLOWED_CERT_MODES.contains(fileMode);
    }
}
