package com.grassroots.cdm.credential;

import com.grassroots.cdm.credential.dto.CredentialRequest;
import com.grassroots.cdm.credential.exception.CredentialExpiredException;
import com.grassroots.cdm.credential.exception.CredentialNotFoundException;
import com.grassroots.cdm.credential.exception.CredentialRotationInProgressException;
import com.grassroots.cdm.credential.exception.CyberArkUnavailableException;
import com.grassroots.cdm.credential.exception.CyberArkUnauthorizedException;
import com.grassroots.cdm.credential.mock.MockCredentialProvider;
import com.grassroots.cdm.integration.CredentialSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockCredentialProviderTest {

    private MockCredentialProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockCredentialProvider();
    }

    @Test
    @DisplayName("Mock Provider: Returns default registered credentials")
    void testDefaultCredentials() {
        CredentialSecret secret = provider.getCredential("GrassrootsSafe", "svc_cdm_deployer", "web01.internal");

        assertThat(secret).isNotNull();
        assertThat(secret.username()).isEqualTo("svc_cdm_deployer");
        assertThat(secret.password()).containsExactly("MockVaultPassword123!".toCharArray());
        assertThat(secret.targetHost()).isEqualTo("web01.internal");
        assertThat(provider.getRetrievalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Mock Provider: Custom registered credentials")
    void testCustomRegisteredCredential() {
        provider.registerMockCredential("CustomSafe", "custom_account", "custom_user", "CustomPass999!", "PrivatePassphrase");

        CredentialSecret secret = provider.getCredential("CustomSafe", "custom_account", "app.local");

        assertThat(secret.username()).isEqualTo("custom_user");
        assertThat(secret.password()).containsExactly("CustomPass999!".toCharArray());
        assertThat(secret.privateKeyPassphrase()).isEqualTo("PrivatePassphrase");
    }

    @Test
    @DisplayName("Mock Provider: Locator resolution")
    void testLocatorResolution() {
        CredentialSecret secret = provider.getCredentialByLocator("cyberark://GrassrootsSafe/Account/svc_cdm_iis");
        assertThat(secret.username()).isEqualTo("svc_cdm_iis");
    }

    @Test
    @DisplayName("Mock Provider: Simulates unavailable CyberArk")
    void testSimulateUnavailable() {
        provider.setSimulateUnavailable(true);

        assertThatThrownBy(() -> provider.getCredential("GrassrootsSafe", "svc_cdm_deployer", null))
                .isInstanceOf(CyberArkUnavailableException.class);
        assertThat(provider.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("Mock Provider: Simulates unauthorized CyberArk")
    void testSimulateUnauthorized() {
        provider.setSimulateUnauthorized(true);

        assertThatThrownBy(() -> provider.getCredential("GrassrootsSafe", "svc_cdm_deployer", null))
                .isInstanceOf(CyberArkUnauthorizedException.class);
    }

    @Test
    @DisplayName("Mock Provider: Simulates missing credential")
    void testSimulateNotFound() {
        provider.setSimulateNotFound(true);

        assertThatThrownBy(() -> provider.getCredential("GrassrootsSafe", "svc_cdm_deployer", null))
                .isInstanceOf(CredentialNotFoundException.class);
    }

    @Test
    @DisplayName("Mock Provider: Simulates credential rotation in progress")
    void testSimulateRotation() {
        provider.setSimulateRotation(true);

        assertThatThrownBy(() -> provider.getCredential("GrassrootsSafe", "svc_cdm_deployer", null))
                .isInstanceOf(CredentialRotationInProgressException.class);
    }

    @Test
    @DisplayName("Mock Provider: Simulates expired credential")
    void testSimulateExpired() {
        provider.setSimulateExpired(true);

        assertThatThrownBy(() -> provider.getCredential("GrassrootsSafe", "svc_cdm_deployer", null))
                .isInstanceOf(CredentialExpiredException.class);
    }
}
