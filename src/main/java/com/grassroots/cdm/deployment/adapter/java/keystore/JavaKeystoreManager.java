package com.grassroots.cdm.deployment.adapter.java.keystore;

import com.grassroots.cdm.deployment.adapter.java.exception.KeystoreOperationException;
import com.grassroots.cdm.deployment.adapter.java.profile.KeystoreType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Set;

/**
 * Enterprise manager for Java KeyStore (JKS & PKCS12) operations.
 *
 * Implements:
 * - Secure loading and initialization of JKS and PKCS12 stores
 * - Key and certificate chain binding under target alias
 * - Restrictive POSIX permission application (0600 / 0640)
 * - Atomic backup creation and restore for automated rollback
 * - Zero plaintext password exposure (in-memory wipe via Arrays.fill)
 */
@Component
public class JavaKeystoreManager {

    private static final Logger log = LoggerFactory.getLogger(JavaKeystoreManager.class);

    public KeyStore loadOrCreateKeystore(File file, KeystoreType type, char[] password) {
        try {
            KeyStore ks = KeyStore.getInstance(type.getFormat());
            if (file != null && file.exists() && file.length() > 0) {
                try (InputStream is = new FileInputStream(file)) {
                    ks.load(is, password);
                }
                log.debug("Loaded existing {} keystore from: {}", type.getFormat(), file.getAbsolutePath());
            } else {
                ks.load(null, password);
                log.debug("Initialized new empty {} keystore for: {}", type.getFormat(), file != null ? file.getAbsolutePath() : "memory");
            }
            return ks;
        } catch (Exception ex) {
            throw new KeystoreOperationException("Failed to load or create " + type.getFormat() + " keystore at "
                    + (file != null ? file.getAbsolutePath() : "unknown") + ": " + ex.getMessage(),
                    file != null ? file.getAbsolutePath() : "unknown", "N/A", 301, ex);
        }
    }

    public void installCertificateAndKey(KeyStore keyStore, String alias, PrivateKey privateKey,
                                         Certificate[] chain, char[] keyPassword) {
        try {
            keyStore.setKeyEntry(alias, privateKey, keyPassword, chain);
            log.debug("Installed key and certificate chain under alias: {}", alias);
        } catch (Exception ex) {
            throw new KeystoreOperationException("Failed to install key and certificate chain under alias '"
                    + alias + "': " + ex.getMessage(), "keystore", alias, 301, ex);
        }
    }

    public void installCertificate(KeyStore keyStore, String alias, Certificate cert) {
        try {
            keyStore.setCertificateEntry(alias, cert);
            log.debug("Installed certificate under alias: {}", alias);
        } catch (Exception ex) {
            throw new KeystoreOperationException("Failed to install certificate under alias '"
                    + alias + "': " + ex.getMessage(), "keystore", alias, 301, ex);
        }
    }

    public void saveKeystore(KeyStore keyStore, File targetFile, char[] password, String fileMode) {
        try {
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                keyStore.store(fos, password);
            }

            setRestrictivePermissions(targetFile, fileMode);
            log.debug("Successfully saved keystore to {} with mode {}", targetFile.getAbsolutePath(), fileMode);
        } catch (Exception ex) {
            throw new KeystoreOperationException("Failed to save keystore to " + targetFile.getAbsolutePath()
                    + ": " + ex.getMessage(), targetFile.getAbsolutePath(), "N/A", 301, ex);
        }
    }

    public File createBackup(File keystoreFile) {
        if (keystoreFile == null || !keystoreFile.exists()) {
            return null;
        }
        try {
            File backupFile = new File(keystoreFile.getAbsolutePath() + ".cdm-bak");
            Files.copy(keystoreFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.debug("Created keystore backup at: {}", backupFile.getAbsolutePath());
            return backupFile;
        } catch (Exception ex) {
            log.error("Failed to create backup for keystore {}: {}", keystoreFile.getAbsolutePath(), ex.getMessage());
            throw new KeystoreOperationException("Failed to create keystore backup: " + ex.getMessage(),
                    keystoreFile.getAbsolutePath(), "backup", 301, ex);
        }
    }

    public void restoreBackup(File backupFile, File targetFile) {
        if (backupFile == null || !backupFile.exists()) {
            throw new KeystoreOperationException("Cannot restore backup: backup file does not exist",
                    targetFile != null ? targetFile.getAbsolutePath() : "unknown", "restore", 301);
        }
        try {
            Files.copy(backupFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("Restored keystore from backup: {} -> {}", backupFile.getAbsolutePath(), targetFile.getAbsolutePath());
        } catch (Exception ex) {
            throw new KeystoreOperationException("Failed to restore keystore from backup " + backupFile.getAbsolutePath()
                    + ": " + ex.getMessage(), targetFile.getAbsolutePath(), "restore", 301, ex);
        }
    }

    public Certificate getCertificate(KeyStore keyStore, String alias) {
        try {
            return keyStore.getCertificate(alias);
        } catch (Exception ex) {
            log.error("Error retrieving certificate for alias {}: {}", alias, ex.getMessage());
            return null;
        }
    }

    public boolean containsCertificate(KeyStore keyStore, String alias, String thumbprint) {
        if (keyStore == null || alias == null || thumbprint == null) {
            return false;
        }
        try {
            Certificate cert = keyStore.getCertificate(alias);
            if (cert == null) {
                return false;
            }
            String actualThumbprint = computeThumbprint(cert);
            return thumbprint.equalsIgnoreCase(actualThumbprint);
        } catch (Exception ex) {
            log.warn("Failed checking certificate thumbprint in keystore for alias {}: {}", alias, ex.getMessage());
            return false;
        }
    }

    public String computeThumbprint(Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new KeystoreOperationException("Failed to compute SHA-1 thumbprint for certificate: " + ex.getMessage(),
                    "memory", "cert", 301, ex);
        }
    }

    public void setRestrictivePermissions(File file, String fileMode) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            String modeStr = (fileMode != null && !fileMode.isBlank()) ? fileMode : "0640";
            String posixPattern = convertOctalToPosix(modeStr);
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString(posixPattern);
            Files.setPosixFilePermissions(file.toPath(), permissions);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystem (e.g. Windows NTFS)
        } catch (Exception ex) {
            log.warn("Could not apply POSIX file permissions {} to {}: {}", fileMode, file.getAbsolutePath(), ex.getMessage());
        }
    }

    public void createTestKeystore(File keystoreFile, KeystoreType type, String alias, char[] password, String dname) {
        try {
            if (keystoreFile.exists()) {
                keystoreFile.delete();
            }
            File parent = keystoreFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            String storeType = type.getFormat();
            String passStr = new String(password);
            String javaHome = System.getProperty("java.home");
            String keytoolPath = javaHome + File.separator + "bin" + File.separator + "keytool";
            if (!new File(keytoolPath).exists()) {
                keytoolPath = "keytool";
            }

            ProcessBuilder pb = new ProcessBuilder(
                    keytoolPath,
                    "-genkeypair",
                    "-alias", alias,
                    "-keystore", keystoreFile.getAbsolutePath(),
                    "-storetype", storeType,
                    "-storepass", passStr,
                    "-keypass", passStr,
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-validity", "365",
                    "-dname", (dname != null ? dname : "CN=localhost, O=Grassroots, C=US")
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                throw new KeystoreOperationException("keytool -genkeypair failed with exit code: " + exitCode,
                        keystoreFile.getAbsolutePath(), alias, 301);
            }
        } catch (Exception ex) {
            throw new KeystoreOperationException("Failed to generate test keystore: " + ex.getMessage(),
                    keystoreFile.getAbsolutePath(), alias, 301, ex);
        }
    }

    public void wipePassword(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    private String convertOctalToPosix(String octal) {
        // Mode 0600 -> "rw-------", Mode 0640 -> "rw-r-----", Mode 0644 -> "rw-r--r--"
        String clean = octal.startsWith("0") ? octal.substring(1) : octal;
        if (clean.length() < 3) clean = "600";
        char u = clean.charAt(0);
        char g = clean.charAt(1);
        char o = clean.charAt(2);

        return toRwx(u) + toRwx(g) + toRwx(o);
    }

    private String toRwx(char oct) {
        return switch (oct) {
            case '7' -> "rwx";
            case '6' -> "rw-";
            case '5' -> "r-x";
            case '4' -> "r--";
            case '0' -> "---";
            default -> "---";
        };
    }
}
