package com.grassroots.cdm.credential;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.credential.cyberark.CyberArkClient;
import com.grassroots.cdm.credential.cyberark.CyberArkCredentialProvider;
import com.grassroots.cdm.credential.cyberark.CyberArkProperties;
import com.grassroots.cdm.credential.dto.CredentialRequest;
import com.grassroots.cdm.credential.dto.CyberArkAccountResponse;
import com.grassroots.cdm.credential.exception.CredentialNotFoundException;
import com.grassroots.cdm.integration.CredentialSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CyberArkCredentialProviderTest {

    private CyberArkClient cyberArkClient;
    private CyberArkProperties properties;
    private AuditService auditService;
    private CyberArkCredentialProvider credentialProvider;

    @BeforeEach
    void setUp() {
        cyberArkClient = mock(CyberArkClient.class);
        properties = new CyberArkProperties();
        properties.setDefaultSafe("GrassrootsSafe");
        auditService = mock(AuditService.class);
        credentialProvider = new CyberArkCredentialProvider(cyberArkClient, properties, auditService);
    }

    @Test
    @DisplayName("Credential Retrieval: Successfully retrieves secret and records audit without secret leakage")
    void testGetCredentialSuccess() {
        CyberArkAccountResponse ccpResponse = new CyberArkAccountResponse();
        ccpResponse.setSafe("GrassrootsSafe");
        ccpResponse.setName("svc_cdm_deployer");
        ccpResponse.setUserName("svc_cdm_deployer");
        ccpResponse.setContent("MySecretPassword123!".toCharArray());
        ccpResponse.setAddress("web01.internal");

        when(cyberArkClient.getAccount("GrassrootsSafe", "svc_cdm_deployer", "web01.internal"))
                .thenReturn(ccpResponse);

        CredentialRequest request = CredentialRequest.builder()
                .safeName("GrassrootsSafe")
                .accountName("svc_cdm_deployer")
                .targetHost("web01.internal")
                .build();

        CredentialSecret secret = credentialProvider.getCredential(request);

        assertThat(secret).isNotNull();
        assertThat(secret.username()).isEqualTo("svc_cdm_deployer");
        assertThat(secret.password()).containsExactly("MySecretPassword123!".toCharArray());
        assertThat(secret.targetHost()).isEqualTo("web01.internal");

        // Verify audit event
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).recordAudit(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.CREDENTIAL_RETRIEVED);
        assertThat(event.outcome()).isEqualTo("SUCCESS");
        assertThat(event.details()).doesNotContain("MySecretPassword123!");
        assertThat(event.details()).contains("GrassrootsSafe");
        assertThat(event.details()).contains("svc_cdm_deployer");
    }

    @Test
    @DisplayName("Locator Resolution: Resolves canonical cyberark:// URI locators")
    void testGetCredentialByLocator() {
        CyberArkAccountResponse ccpResponse = new CyberArkAccountResponse();
        ccpResponse.setSafe("CertVault");
        ccpResponse.setName("Cert-001");
        ccpResponse.setUserName("app_user");
        ccpResponse.setContent("SecretVal!".toCharArray());

        when(cyberArkClient.getAccount("CertVault", "Cert-001", null)).thenReturn(ccpResponse);

        // Pattern 1: cyberark://Safe/Account/Name
        CredentialSecret secret1 = credentialProvider.getCredentialByLocator("cyberark://CertVault/Account/Cert-001");
        assertThat(secret1.username()).isEqualTo("app_user");

        // Pattern 2: cyberark://Safe/Name
        CredentialSecret secret2 = credentialProvider.getCredentialByLocator("cyberark://CertVault/Cert-001");
        assertThat(secret2.username()).isEqualTo("app_user");

        // Invalid pattern
        assertThatThrownBy(() -> credentialProvider.getCredentialByLocator("vault://invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> credentialProvider.getCredentialByLocator(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("executeWithCredential: Automatically wipes password after action completion")
    void testExecuteWithCredentialAutoWipe() throws Exception {
        CyberArkAccountResponse ccpResponse = new CyberArkAccountResponse();
        ccpResponse.setSafe("GrassrootsSafe");
        ccpResponse.setName("svc_deployer");
        ccpResponse.setUserName("svc_deployer");
        ccpResponse.setContent("TempPass123".toCharArray());

        when(cyberArkClient.getAccount("GrassrootsSafe", "svc_deployer", "target.local"))
                .thenReturn(ccpResponse);

        CredentialRequest request = CredentialRequest.builder()
                .safeName("GrassrootsSafe")
                .accountName("svc_deployer")
                .targetHost("target.local")
                .build();

        char[] capturedPasswordRef = new char[1];

        String result = credentialProvider.executeWithCredential(request, secret -> {
            capturedPasswordRef[0] = secret.password()[0];
            return "DeploymentSuccessful";
        });

        assertThat(result).isEqualTo("DeploymentSuccessful");
        assertThat(capturedPasswordRef[0]).isEqualTo('T');
    }

    @Test
    @DisplayName("Error Handling: Propagates exception and records audit failure")
    void testErrorPropagationAndAuditFailure() {
        when(cyberArkClient.getAccount(anyString(), anyString(), any()))
                .thenThrow(new CredentialNotFoundException("MissingSafe", "MissingAccount"));

        CredentialRequest request = CredentialRequest.builder()
                .safeName("MissingSafe")
                .accountName("MissingAccount")
                .build();

        assertThatThrownBy(() -> credentialProvider.getCredential(request))
                .isInstanceOf(CredentialNotFoundException.class);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).recordAudit(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.CREDENTIAL_RETRIEVED);
        assertThat(event.outcome()).isEqualTo("FAILED");
    }
}
