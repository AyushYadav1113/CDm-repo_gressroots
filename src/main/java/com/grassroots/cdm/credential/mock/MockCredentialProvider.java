package com.grassroots.cdm.credential.mock;

import com.grassroots.cdm.credential.CredentialProvider;
import com.grassroots.cdm.credential.dto.CredentialRequest;
import com.grassroots.cdm.credential.exception.CredentialExpiredException;
import com.grassroots.cdm.credential.exception.CredentialNotFoundException;
import com.grassroots.cdm.credential.exception.CredentialRotationInProgressException;
import com.grassroots.cdm.credential.exception.CyberArkUnavailableException;
import com.grassroots.cdm.credential.exception.CyberArkUnauthorizedException;
import com.grassroots.cdm.integration.CredentialSecret;
import com.grassroots.cdm.integration.CyberArkVaultClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock CredentialProvider for unit/integration tests and local development.
 * Simulates CyberArk vault behavior without connecting to real infrastructure.
 */
@Component
@ConditionalOnProperty(name = "cdm.cyberark.mock-enabled", havingValue = "true")
public class MockCredentialProvider implements CredentialProvider, CyberArkVaultClient {

    private static final Logger log = LoggerFactory.getLogger(MockCredentialProvider.class);

    private final Map<String, CredentialSecret> mockStore = new ConcurrentHashMap<>();
    private final AtomicInteger retrievalCount = new AtomicInteger(0);

    private volatile boolean simulateUnavailable = false;
    private volatile boolean simulateUnauthorized = false;
    private volatile boolean simulateNotFound = false;
    private volatile boolean simulateRotation = false;
    private volatile boolean simulateExpired = false;

    private volatile String lastRetrievedSafe;
    private volatile String lastRetrievedAccount;

    public MockCredentialProvider() {
        initDefaultCredentials();
    }

    private void initDefaultCredentials() {
        registerMockCredential("GrassrootsSafe", "svc_cdm_deployer", "svc_cdm_deployer", "MockVaultPassword123!");
        registerMockCredential("GrassrootsSafe", "svc_cdm_iis", "svc_cdm_iis", "IisVaultPassword123!");
        registerMockCredential("GrassrootsSafe", "svc_cdm_apache", "svc_cdm_apache", "ApacheVaultPassword123!");
        registerMockCredential("GrassrootsSafe", "svc_cdm_nginx", "svc_cdm_nginx", "NginxVaultPassword123!");
        registerMockCredential("GrassrootsSafe", "svc_cdm_java_keystore", "svc_cdm_java", "JavaVaultPassword123!");
        registerMockCredential("GrassrootsSafe", "svc_cdm_tomcat", "svc_cdm_tomcat", "TomcatVaultPassword123!");
        registerMockCredential("GrassrootsSafe", "svc_cdm_default", "svc_cdm_default", "DefaultVaultPassword123!");
        registerMockCredential("CDM_PROD_SAFE", "svc_cdm_default", "svc_cdm_default", "ProdSafeDefaultPassword123!");
    }

    public void registerMockCredential(String safe, String account, String username, String password) {
        String key = buildKey(safe, account);
        mockStore.put(key, new CredentialSecret(username, password.toCharArray(), null, null));
    }

    public void registerMockCredential(String safe, String account, String username, String password, String privateKeyPassphrase) {
        String key = buildKey(safe, account);
        mockStore.put(key, new CredentialSecret(username, password.toCharArray(), privateKeyPassphrase, null));
    }

    @Override
    public CredentialSecret getCredential(CredentialRequest request) {
        retrievalCount.incrementAndGet();
        String safe = request.getSafeName() != null ? request.getSafeName() : "GrassrootsSafe";
        String account = request.getAccountName();
        this.lastRetrievedSafe = safe;
        this.lastRetrievedAccount = account;

        if (simulateUnavailable) {
            throw new CyberArkUnavailableException("Simulated CyberArk CCP connection timeout", "https://mock-cyberark/AIMWebService");
        }
        if (simulateUnauthorized) {
            throw new CyberArkUnauthorizedException("Simulated CyberArk AppID unauthorized", "CDM_Application", safe, 403);
        }
        if (simulateNotFound) {
            throw new CredentialNotFoundException(safe, account);
        }
        if (simulateRotation) {
            throw new CredentialRotationInProgressException(safe, account);
        }
        if (simulateExpired) {
            throw new CredentialExpiredException(safe, account, "Simulated password policy expiration");
        }

        String key = buildKey(safe, account);
        CredentialSecret stored = mockStore.get(key);
        if (stored == null) {
            // Also try fallback by account name alone
            for (Map.Entry<String, CredentialSecret> entry : mockStore.entrySet()) {
                if (entry.getKey().endsWith("/" + account)) {
                    stored = entry.getValue();
                    break;
                }
            }
        }

        if (stored == null) {
            log.warn("Mock credential not found for Safe '{}', Account '{}'", safe, account);
            throw new CredentialNotFoundException(safe, account);
        }

        // Return a copy so caller can wipe their instance without destroying mock store
        char[] passwordCopy = stored.password() != null ? stored.password().clone() : null;
        return new CredentialSecret(stored.username(), passwordCopy, stored.privateKeyPassphrase(), request.getTargetHost());
    }

    @Override
    public CredentialSecret getCredentialByLocator(String secretLocator) {
        if (secretLocator == null || !secretLocator.startsWith("cyberark://")) {
            throw new IllegalArgumentException("Invalid CyberArk secret locator format: " + secretLocator);
        }

        URI uri = URI.create(secretLocator);
        String safe = uri.getHost();
        String path = uri.getPath();
        if (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path != null && path.startsWith("Account/")) {
            path = path.substring("Account/".length());
        }

        return getCredential(safe, path, null);
    }

    @Override
    public CredentialSecret retrieveCredential(String safeName, String accountName, String targetHost) {
        return getCredential(CredentialRequest.builder()
                .safeName(safeName)
                .accountName(accountName)
                .targetHost(targetHost)
                .build());
    }

    @Override
    public boolean isAvailable() {
        return !simulateUnavailable;
    }

    public void reset() {
        simulateUnavailable = false;
        simulateUnauthorized = false;
        simulateNotFound = false;
        simulateRotation = false;
        simulateExpired = false;
        retrievalCount.set(0);
        mockStore.clear();
        initDefaultCredentials();
    }

    public int getRetrievalCount() {
        return retrievalCount.get();
    }

    public String getLastRetrievedSafe() {
        return lastRetrievedSafe;
    }

    public String getLastRetrievedAccount() {
        return lastRetrievedAccount;
    }

    public void setSimulateUnavailable(boolean simulateUnavailable) {
        this.simulateUnavailable = simulateUnavailable;
    }

    public void setSimulateUnauthorized(boolean simulateUnauthorized) {
        this.simulateUnauthorized = simulateUnauthorized;
    }

    public void setSimulateNotFound(boolean simulateNotFound) {
        this.simulateNotFound = simulateNotFound;
    }

    public void setSimulateRotation(boolean simulateRotation) {
        this.simulateRotation = simulateRotation;
    }

    public void setSimulateExpired(boolean simulateExpired) {
        this.simulateExpired = simulateExpired;
    }

    private String buildKey(String safe, String account) {
        return safe.toLowerCase() + "/" + account.toLowerCase();
    }
}
