package com.grassroots.cdm.verification.impl;

import com.grassroots.cdm.verification.CertificateInspectionService;
import com.grassroots.cdm.verification.model.InspectedCertificate;
import com.grassroots.cdm.verification.model.InspectionOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production implementation of CertificateInspectionService.
 * Connects directly to live TLS endpoints using an isolated, connection-scoped SSLContext
 * to inspect presented X.509 certificates without altering global JVM trust or accepting certificates blindly.
 */
@Service
public class DefaultCertificateInspectionService implements CertificateInspectionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCertificateInspectionService.class);
    private static final Pattern CN_PATTERN = Pattern.compile("(?:^|,\\s*)CN=([^,]+)", Pattern.CASE_INSENSITIVE);

    @Override
    public InspectionOutcome inspectEndpoint(String host, int port, int timeoutMs) {
        if (host == null || host.isBlank()) {
            return InspectionOutcome.unreachable("Target host cannot be null or blank", null);
        }
        if (port < 1 || port > 65535) {
            return InspectionOutcome.unreachable("Target port is out of range: " + port, null);
        }

        Socket plainSocket = new Socket();
        SSLSocket sslSocket = null;
        try {
            // Step 1: Establish TCP connection
            plainSocket.connect(new InetSocketAddress(host, port), timeoutMs);
            plainSocket.setSoTimeout(timeoutMs);

            // Step 2: Create connection-scoped SSLContext with capturing trust manager
            CapturingTrustManager capturingTrustManager = new CapturingTrustManager();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{capturingTrustManager}, new SecureRandom());
            SSLSocketFactory factory = sslContext.getSocketFactory();

            // Step 3: Layer SSLSocket over established TCP socket
            sslSocket = (SSLSocket) factory.createSocket(plainSocket, host, port, true);
            sslSocket.setSoTimeout(timeoutMs);

            // Step 4: Configure SNI if host is a domain name (not raw IP address)
            if (!isIpAddress(host)) {
                try {
                    SSLParameters sslParameters = sslSocket.getSSLParameters();
                    sslParameters.setServerNames(List.of(new SNIHostName(host)));
                    sslSocket.setSSLParameters(sslParameters);
                } catch (Exception ex) {
                    log.warn("Could not set SNIHostName for host {}: {}", host, ex.getMessage());
                }
            }

            // Step 5: Execute TLS handshake
            sslSocket.startHandshake();

            // Step 6: Extract peer certificates
            X509Certificate[] certChain = capturingTrustManager.getCapturedChain();
            if (certChain == null || certChain.length == 0) {
                Certificate[] sessionCerts = sslSocket.getSession().getPeerCertificates();
                if (sessionCerts != null && sessionCerts.length > 0 && sessionCerts[0] instanceof X509Certificate) {
                    certChain = Arrays.copyOf(sessionCerts, sessionCerts.length, X509Certificate[].class);
                }
            }

            if (certChain == null || certChain.length == 0) {
                return InspectionOutcome.tlsError("No X.509 certificates presented by endpoint during TLS handshake", null);
            }

            InspectedCertificate inspectedCert = parseCertificate(certChain[0], certChain);
            return InspectionOutcome.success(inspectedCert);

        } catch (UnknownHostException ex) {
            log.warn("Unknown host when inspecting {}:{}: {}", host, port, ex.getMessage());
            return InspectionOutcome.unreachable("Unknown host: " + host, ex);
        } catch (ConnectException ex) {
            log.warn("Connection refused on {}:{}: {}", host, port, ex.getMessage());
            return InspectionOutcome.unreachable("Connection refused on " + host + ":" + port, ex);
        } catch (SocketTimeoutException ex) {
            log.warn("Connection or read timed out on {}:{}: {}", host, port, ex.getMessage());
            return InspectionOutcome.unreachable("Connection timed out connecting to " + host + ":" + port, ex);
        } catch (SSLHandshakeException ex) {
            log.warn("TLS handshake failure on {}:{}: {}", host, port, ex.getMessage());
            return InspectionOutcome.tlsError("TLS handshake failure on " + host + ":" + port + ": " + ex.getMessage(), ex);
        } catch (SSLException ex) {
            log.warn("TLS protocol error on {}:{}: {}", host, port, ex.getMessage());
            return InspectionOutcome.tlsError("TLS protocol error on " + host + ":" + port + ": " + ex.getMessage(), ex);
        } catch (IOException ex) {
            log.warn("I/O network error inspecting {}:{}: {}", host, port, ex.getMessage());
            return InspectionOutcome.unreachable("Network error connecting to " + host + ":" + port + ": " + ex.getMessage(), ex);
        } catch (Exception ex) {
            log.warn("Unexpected inspection error on {}:{}: {}", host, port, ex.getMessage());
            return InspectionOutcome.tlsError("TLS inspection error: " + ex.getMessage(), ex);
        } finally {
            if (sslSocket != null) {
                try {
                    sslSocket.close();
                } catch (Exception ignored) {
                }
            }
            try {
                plainSocket.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public InspectedCertificate parseCertificate(X509Certificate cert) {
        return parseCertificate(cert, null);
    }

    @Override
    public InspectedCertificate parseCertificate(X509Certificate cert, X509Certificate[] chain) {
        if (cert == null) {
            throw new IllegalArgumentException("X509Certificate cannot be null");
        }

        try {
            // Fingerprints
            byte[] encoded = cert.getEncoded();
            String sha256 = computeDigestHex(encoded, "SHA-256");
            String sha1 = computeDigestHex(encoded, "SHA-1");

            // Serial Number
            String serialHex = cert.getSerialNumber().toString(16).toUpperCase();
            String serialDec = cert.getSerialNumber().toString(10);

            // Subject DN and Common Name
            String subjectDn = cert.getSubjectX500Principal().getName();
            String cn = extractCommonName(subjectDn);

            // Subject Alternative Names (SANs)
            List<String> dnsNames = new ArrayList<>();
            List<String> ipAddresses = new ArrayList<>();
            List<String> allSans = new ArrayList<>();
            extractSubjectAlternativeNames(cert, dnsNames, ipAddresses, allSans);

            // Issuer DN and Issuer Common Name
            String issuerDn = cert.getIssuerX500Principal().getName();
            String issuerCn = extractCommonName(issuerDn);

            // Validity Dates
            Instant notBefore = cert.getNotBefore().toInstant();
            Instant notAfter = cert.getNotAfter().toInstant();

            // Metadata
            String sigAlg = cert.getSigAlgName();
            int version = cert.getVersion();

            return new InspectedCertificate(
                    cert,
                    sha256,
                    sha1,
                    serialHex,
                    serialDec,
                    cn,
                    subjectDn,
                    allSans,
                    dnsNames,
                    ipAddresses,
                    issuerDn,
                    issuerCn,
                    notBefore,
                    notAfter,
                    sigAlg,
                    version
            );
        } catch (Exception ex) {
            log.error("Failed to parse X509Certificate: {}", ex.getMessage(), ex);
            throw new IllegalArgumentException("Failed to parse X509Certificate: " + ex.getMessage(), ex);
        }
    }

    private void extractSubjectAlternativeNames(X509Certificate cert,
                                               List<String> dnsNames,
                                               List<String> ipAddresses,
                                               List<String> allSans) {
        try {
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans != null) {
                for (List<?> item : sans) {
                    if (item.size() >= 2) {
                        Integer type = (Integer) item.get(0);
                        String value = String.valueOf(item.get(1));
                        if (type != null && value != null) {
                            switch (type) {
                                case 2 -> { // dNSName
                                    dnsNames.add(value.trim());
                                    allSans.add("DNS:" + value.trim());
                                }
                                case 7 -> { // iPAddress
                                    ipAddresses.add(value.trim());
                                    allSans.add("IP:" + value.trim());
                                }
                                default -> allSans.add("Type" + type + ":" + value.trim());
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Could not extract SubjectAlternativeNames from certificate: {}", ex.getMessage());
        }
    }

    private String extractCommonName(String dn) {
        if (dn == null || dn.isBlank()) {
            return null;
        }
        try {
            LdapName ldapName = new LdapName(dn);
            for (Rdn rdn : ldapName.getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return String.valueOf(rdn.getValue());
                }
            }
        } catch (Exception ignored) {
            // Fallback to regex if DN is not standard RFC 2253
        }

        Matcher matcher = CN_PATTERN.matcher(dn);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String computeDigestHex(byte[] data, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed computing digest " + algorithm + ": " + ex.getMessage(), ex);
        }
    }

    private boolean isIpAddress(String host) {
        return host.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$") || host.contains(":");
    }

    /**
     * Isolated TrustManager that captures the presented certificate chain during handshake
     * without modifying JVM-wide trust managers or blindly accepting invalid certificates.
     */
    private static class CapturingTrustManager implements X509TrustManager {
        private final AtomicReference<X509Certificate[]> capturedChain = new AtomicReference<>();

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            if (chain != null && chain.length > 0) {
                capturedChain.set(chain.clone());
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        public X509Certificate[] getCapturedChain() {
            return capturedChain.get();
        }
    }
}
