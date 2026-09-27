package com.grassroots.cdm.credential.cyberark;

import com.grassroots.cdm.audit.AuditAction;
import com.grassroots.cdm.audit.AuditEvent;
import com.grassroots.cdm.audit.AuditService;
import com.grassroots.cdm.credential.CredentialProvider;
import com.grassroots.cdm.credential.dto.CredentialRequest;
import com.grassroots.cdm.credential.dto.CyberArkAccountResponse;
import com.grassroots.cdm.integration.CredentialSecret;
import com.grassroots.cdm.integration.CyberArkVaultClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;

/**
 * Production implementation of CredentialProvider and CyberArkVaultClient.
 * Bridges CDM workflows to CyberArk CCP for on-demand credential retrieval.
 * Enforces zero database persistence of passwords and strict memory-clearing boundaries.
 */
@Service
@ConditionalOnProperty(name = "cdm.cyberark.mock-enabled", havingValue = "false", matchIfMissing = true)
public class CyberArkCredentialProvider implements CredentialProvider, CyberArkVaultClient {

    private static final Logger log = LoggerFactory.getLogger(CyberArkCredentialProvider.class);

    private final CyberArkClient cyberArkClient;
    private final CyberArkProperties properties;
    private final AuditService auditService;

    @Autowired
    public CyberArkCredentialProvider(CyberArkClient cyberArkClient,
                                     CyberArkProperties properties,
                                     @Autowired(required = false) AuditService auditService) {
        this.cyberArkClient = cyberArkClient;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Override
    public CredentialSecret getCredential(CredentialRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CredentialRequest cannot be null");
        }

        String safe = request.getSafeName() != null ? request.getSafeName() : properties.getDefaultSafe();
        String account = request.getAccountName();
        String targetHost = request.getTargetHost();

        log.debug("Initiating credential retrieval for Safe '{}', Account '{}', Host '{}'",
                safe, account, targetHost);

        try {
            CyberArkAccountResponse response = cyberArkClient.getAccount(safe, account, targetHost);

            CredentialSecret secret = new CredentialSecret(
                    response.getUserName(),
                    response.getContent(),
                    response.getPrivateKeyPassphrase(),
                    targetHost
            );

            recordAuditSuccess(safe, account, targetHost);
            return secret;

        } catch (Exception ex) {
            recordAuditFailure(safe, account, targetHost, ex.getMessage());
            throw ex;
        }
    }

    @Override
    public CredentialSecret getCredentialByLocator(String secretLocator) {
        if (secretLocator == null || !secretLocator.startsWith("cyberark://")) {
            throw new IllegalArgumentException("Invalid CyberArk secret locator format (expected cyberark://Safe/Account): "
                    + secretLocator);
        }

        try {
            URI uri = URI.create(secretLocator);
            String safe = uri.getHost();
            String path = uri.getPath(); // e.g. /Account/Cert-001 or /Cert-001

            if (safe == null || safe.isBlank()) {
                throw new IllegalArgumentException("Missing Safe identifier in locator: " + secretLocator);
            }

            if (path == null || path.length() <= 1) {
                throw new IllegalArgumentException("Missing Account identifier in locator: " + secretLocator);
            }

            // Remove leading slash
            String account = path.substring(1);
            if (account.startsWith("Account/")) {
                account = account.substring("Account/".length());
            }

            return getCredential(safe, account, null);

        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse CyberArk secret locator '" + secretLocator + "': "
                    + ex.getMessage(), ex);
        }
    }

    @Override
    public CredentialSecret retrieveCredential(String safeName, String accountName, String targetHost) {
        return getCredential(safeName, accountName, targetHost);
    }

    @Override
    public boolean isAvailable() {
        return cyberArkClient.ping();
    }

    private void recordAuditSuccess(String safe, String account, String targetHost) {
        if (auditService != null) {
            try {
                auditService.recordAudit(AuditEvent.create(
                        AuditAction.CREDENTIAL_RETRIEVED,
                        "CyberArkSafe",
                        safe + "/" + account,
                        "CyberArkCredentialProvider",
                        "SUCCESS",
                        "Retrieved transient credential for host: " + (targetHost != null ? targetHost : "[ANY]")
                                + " from safe: " + safe + ", account: " + account,
                        targetHost != null ? targetHost : "127.0.0.1"
                ));
            } catch (Exception ex) {
                log.warn("Could not record audit log for credential retrieval: {}", ex.getMessage());
            }
        }
    }

    private void recordAuditFailure(String safe, String account, String targetHost, String reason) {
        if (auditService != null) {
            try {
                auditService.recordAudit(AuditEvent.create(
                        AuditAction.CREDENTIAL_RETRIEVED,
                        "CyberArkSafe",
                        safe + "/" + account,
                        "CyberArkCredentialProvider",
                        "FAILED",
                        "Failed retrieving credential from safe: " + safe + ", account: " + account + ": " + reason,
                        targetHost != null ? targetHost : "127.0.0.1"
                ));
            } catch (Exception ex) {
                log.warn("Could not record audit log for credential failure: {}", ex.getMessage());
            }
        }
    }
}
