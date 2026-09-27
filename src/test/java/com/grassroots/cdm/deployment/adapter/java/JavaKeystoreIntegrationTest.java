package com.grassroots.cdm.deployment.adapter.java;

import com.grassroots.cdm.deployment.adapter.java.keystore.JavaKeystoreManager;
import com.grassroots.cdm.deployment.adapter.java.model.JavaDeploymentCommand;
import com.grassroots.cdm.deployment.adapter.java.profile.JavaDeploymentProfile;
import com.grassroots.cdm.deployment.adapter.java.profile.JavaDeploymentProfileResolver;
import com.grassroots.cdm.deployment.adapter.java.profile.KeystoreType;
import com.grassroots.cdm.deployment.adapter.java.profile.RestartStrategy;
import com.grassroots.cdm.entity.CertificateInstallation;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JavaKeystoreIntegrationTest {

    private JavaKeystoreManager keystoreManager;
    private JavaDeploymentProfileResolver profileResolver;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        keystoreManager = new JavaKeystoreManager();
        profileResolver = new JavaDeploymentProfileResolver();
    }

    @Test
    @DisplayName("PKCS12: Create, inspect, and verify temporary PKCS12 keystore")
    void testCreateAndVerifyPkcs12Keystore() {
        File p12File = tempDir.resolve("keystore.p12").toFile();
        char[] password = new char[]{'C', 'h', 'a', 'n', 'g', 'e', 'I', 't', '1', '2', '3'};

        keystoreManager.createTestKeystore(p12File, KeystoreType.PKCS12, "springboot", password, "CN=springboot.local, O=Test, C=US");

        assertThat(p12File).exists();
        assertThat(p12File.length()).isGreaterThan(0);

        KeyStore loaded = keystoreManager.loadOrCreateKeystore(p12File, KeystoreType.PKCS12, password);
        Certificate cert = keystoreManager.getCertificate(loaded, "springboot");
        assertThat(cert).isNotNull();

        String thumbprint = keystoreManager.computeThumbprint(cert);
        assertThat(thumbprint).isNotBlank().hasSize(40);
        assertThat(keystoreManager.containsCertificate(loaded, "springboot", thumbprint)).isTrue();

        keystoreManager.wipePassword(password);
        assertThat(password).containsOnly('\0');
    }

    @Test
    @DisplayName("JKS: Create, inspect, and verify temporary JKS keystore")
    void testCreateAndVerifyJksKeystore() {
        File jksFile = tempDir.resolve("keystore.jks").toFile();
        char[] password = new char[]{'T', 'e', 'm', 'p', 'P', 'a', 's', 's', '4', '5', '6'};

        keystoreManager.createTestKeystore(jksFile, KeystoreType.JKS, "tomcat", password, "CN=tomcat.local, O=Test, C=US");

        assertThat(jksFile).exists();
        assertThat(jksFile.length()).isGreaterThan(0);

        KeyStore loaded = keystoreManager.loadOrCreateKeystore(jksFile, KeystoreType.JKS, password);
        Certificate cert = keystoreManager.getCertificate(loaded, "tomcat");
        assertThat(cert).isNotNull();

        String thumbprint = keystoreManager.computeThumbprint(cert);
        assertThat(keystoreManager.containsCertificate(loaded, "tomcat", thumbprint)).isTrue();

        keystoreManager.wipePassword(password);
    }

    @Test
    @DisplayName("Backup & Restore: atomic backup creation and restore on temporary keystore")
    void testBackupAndRestoreKeystore() throws Exception {
        File origFile = tempDir.resolve("app-keystore.p12").toFile();
        char[] password = new char[]{'S', 'e', 'c', 'u', 'r', 'e', '1', '2', '3'};

        keystoreManager.createTestKeystore(origFile, KeystoreType.PKCS12, "app", password, "CN=v1.example.com");
        long initialSize = origFile.length();

        // 1. Create backup (.cdm-bak)
        File backupFile = keystoreManager.createBackup(origFile);
        assertThat(backupFile).exists();
        assertThat(backupFile.getName()).endsWith(".cdm-bak");
        assertThat(backupFile.length()).isEqualTo(initialSize);

        // 2. Corrupt or overwrite active file
        Files.writeString(origFile.toPath(), "corrupted-content");

        // 3. Restore backup
        keystoreManager.restoreBackup(backupFile, origFile);
        assertThat(origFile.length()).isEqualTo(initialSize);

        // 4. Verify restored keystore loads cleanly
        KeyStore restored = keystoreManager.loadOrCreateKeystore(origFile, KeystoreType.PKCS12, password);
        assertThat(keystoreManager.getCertificate(restored, "app")).isNotNull();

        keystoreManager.wipePassword(password);
    }

    @Test
    @DisplayName("Security: Enforce zero password and private key exposure in profile and command toString")
    void testZeroPasswordLeakageInProfileAndCommand() {
        JavaDeploymentProfile profile = JavaDeploymentProfile.builder()
                .profileName("secure-profile")
                .keystoreType(KeystoreType.PKCS12)
                .keystoreLocation("/opt/app/keystore.p12")
                .keyAlias("my-key")
                .keystorePasswordVaultRef("cyberark://GrassrootsSafe/Account/SecretPass123")
                .keyPasswordVaultRef("cyberark://GrassrootsSafe/Account/KeyPass456")
                .restartStrategy(RestartStrategy.SYSTEMD_SERVICE)
                .serviceName("api-server")
                .fileMode("0600")
                .build();

        String profileStr = profile.toString();
        assertThat(profileStr).doesNotContain("SecretPass123");
        assertThat(profileStr).doesNotContain("KeyPass456");
        assertThat(profileStr).contains("[REDACTED]");

        JavaDeploymentCommand command = new JavaDeploymentCommand();
        command.setKeystoreLocation("/opt/app/keystore.p12");
        command.setKeystorePasswordVaultRef("cyberark://Secret");
        command.setKeyPasswordVaultRef("cyberark://KeySecret");

        String commandStr = command.toString();
        assertThat(commandStr).doesNotContain("Secret");
        assertThat(commandStr).contains("[REDACTED]");
    }

    @Test
    @DisplayName("Profile Resolver: accurately parses key-value binding configurations")
    void testProfileResolverKeyValues() {
        TargetServer server = new TargetServer("tomcat01.internal", "10.0.1.5",
                ServerOperatingSystem.LINUX_RHEL, ServerTechnology.TOMCAT, EnvironmentType.STAGING);

        CertificateRecord cert = new CertificateRecord();
        cert.setId(UUID.randomUUID());
        cert.setCommonName("my-tomcat.internal");
        cert.setThumbprint("THUMBPRINT12345");

        String bindingInfo = "keystoreLocation=/opt/tomcat/conf/keystore.p12;"
                + "alias=tomcat-ssl;"
                + "type=PKCS12;"
                + "restartStrategy=GRACEFUL_RELOAD;"
                + "serviceName=tomcat9;"
                + "fileMode=0640;"
                + "owner=tomcat;"
                + "group=tomcat;"
                + "appConfigLocation=/opt/tomcat/conf/server.xml";

        CertificateInstallation installation = new CertificateInstallation(
                cert, server, ServerTechnology.TOMCAT, bindingInfo, 8443
        );

        DeploymentJob job = new DeploymentJob();
        job.setId(UUID.randomUUID());
        job.setTargetServer(server);
        job.setNewCertificate(cert);
        job.setInstallation(installation);
        job.setTargetPort(8443);

        JavaDeploymentProfile profile = profileResolver.resolveProfile(job);

        assertThat(profile.getKeystoreLocation()).isEqualTo("/opt/tomcat/conf/keystore.p12");
        assertThat(profile.getKeyAlias()).isEqualTo("tomcat-ssl");
        assertThat(profile.getKeystoreType()).isEqualTo(KeystoreType.PKCS12);
        assertThat(profile.getRestartStrategy()).isEqualTo(RestartStrategy.GRACEFUL_RELOAD);
        assertThat(profile.getServiceName()).isEqualTo("tomcat9");
        assertThat(profile.getFileMode()).isEqualTo("0640");
        assertThat(profile.getOwner()).isEqualTo("tomcat");
        assertThat(profile.getGroup()).isEqualTo("tomcat");
        assertThat(profile.getAppConfigLocation()).isEqualTo("/opt/tomcat/conf/server.xml");
        assertThat(profile.isSecurePermissions()).isTrue();
    }
}
