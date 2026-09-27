package com.grassroots.cdm.credential;

import com.grassroots.cdm.integration.CredentialSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialSecretTest {

    @Test
    @DisplayName("Security: Memory wiping zeroes out password character array")
    void testPasswordWiping() {
        char[] password = new char[]{'S', 'u', 'p', 'e', 'r', 'S', 'e', 'c', 'r', 'e', 't', '1', '2', '3'};
        CredentialSecret secret = new CredentialSecret("deployer", password, null, "web01.internal");

        assertThat(secret.password()).containsExactly('S', 'u', 'p', 'e', 'r', 'S', 'e', 'c', 'r', 'e', 't', '1', '2', '3');

        secret.wipe();

        for (char c : secret.password()) {
            assertThat(c).isEqualTo('\0');
        }
    }

    @Test
    @DisplayName("Security: AutoCloseable contract zeroes password upon try-with-resources completion")
    void testAutoCloseableWiping() {
        char[] password = new char[]{'A', 'u', 't', 'o', 'C', 'l', 'o', 's', 'e', 'P', 'a', 's', 's'};
        char[] passwordRef;

        try (CredentialSecret secret = new CredentialSecret("appuser", password, "KeyPass", "app01.internal")) {
            passwordRef = secret.password();
            assertThat(passwordRef[0]).isEqualTo('A');
        }

        // Outside try-with-resources: password must be wiped
        for (char c : passwordRef) {
            assertThat(c).isEqualTo('\0');
        }
    }

    @Test
    @DisplayName("Security: toString strictly redacts passwords and private keys to prevent log leaks")
    void testToStringRedaction() {
        char[] password = new char[]{'P', 'a', 's', 's', 'w', 'o', 'r', 'd', '!', '!'};
        CredentialSecret secret = new CredentialSecret("svc_account", password, "TopSecretPassphrase", "host.domain.com");

        String str = secret.toString();

        assertThat(str).doesNotContain("Password!!");
        assertThat(str).doesNotContain("TopSecretPassphrase");
        assertThat(str).contains("[REDACTED]");
        assertThat(str).contains("svc_account");
        assertThat(str).contains("host.domain.com");
    }
}
