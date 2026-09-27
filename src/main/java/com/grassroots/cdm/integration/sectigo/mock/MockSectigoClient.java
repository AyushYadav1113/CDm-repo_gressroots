package com.grassroots.cdm.integration.sectigo.mock;

import com.grassroots.cdm.integration.sectigo.SectigoClient;
import com.grassroots.cdm.integration.sectigo.dto.SectigoCertificateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Mock implementation of {@link SectigoClient} for local sandbox development and offline testing.
 * Activated when cdm.sectigo.mock-enabled=true.
 */
@Component
@ConditionalOnProperty(name = "cdm.sectigo.mock-enabled", havingValue = "true")
public class MockSectigoClient implements SectigoClient {

    private static final Logger log = LoggerFactory.getLogger(MockSectigoClient.class);

    private final List<SectigoCertificateDto> mockCertificates = new ArrayList<>();

    public MockSectigoClient() {
        initMockData();
    }

    private void initMockData() {
        // Mock Certificate 1: Active, Newly Issued
        SectigoCertificateDto cert1 = new SectigoCertificateDto();
        cert1.setId("mock-sectigo-1001");
        cert1.setCommonName("payments.grassroots.internal");
        cert1.setSubjectAlternativeNames(List.of("payments.grassroots.internal", "pay.grassroots.internal"));
        cert1.setSerialNumber("4A2B6C8D0E1F3A5B");
        cert1.setSha256Fingerprint("A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9B0C1D2E3F4A5B6C7D8E9F0A1B2");
        cert1.setIssuer("Sectigo RSA Organization Validation Secure Server CA");
        cert1.setValidFrom("2026-01-01T00:00:00Z");
        cert1.setValidTo("2028-01-01T23:59:59Z");
        cert1.setStatus("ISSUED");
        cert1.setOrderId("order-mock-5001");
        cert1.setKeyAlgorithm("RSA 2048");
        cert1.setSignatureAlgorithm("SHA256withRSA");
        mockCertificates.add(cert1);

        // Mock Certificate 2: Renewed Certificate replacing older cert
        SectigoCertificateDto cert2 = new SectigoCertificateDto();
        cert2.setId("mock-sectigo-1002");
        cert2.setCommonName("auth.grassroots.internal");
        cert2.setSubjectAlternativeNames(List.of("auth.grassroots.internal", "sso.grassroots.internal"));
        cert2.setSerialNumber("7E8F9A0B1C2D3E4F");
        cert2.setSha256Fingerprint("F1E2D3C4B5A697887766554433221100FFEEDDCCBBAA99887766554433221100");
        cert2.setIssuer("Sectigo RSA Domain Validation Secure Server CA");
        cert2.setValidFrom("2026-06-01T00:00:00Z");
        cert2.setValidTo("2028-06-01T23:59:59Z");
        cert2.setStatus("ISSUED");
        cert2.setOrderId("order-mock-5002");
        cert2.setRenewedFromCertificateId("mock-sectigo-1000"); // Points to predecessor
        cert2.setKeyAlgorithm("RSA 2048");
        cert2.setSignatureAlgorithm("SHA256withRSA");
        mockCertificates.add(cert2);
    }

    @Override
    public List<SectigoCertificateDto> fetchCertificates(int size, int position, String status, String correlationId) {
        log.info("MockSectigoClient: fetching page size={}, position={}, status={}, correlationId={}",
                size, position, status, correlationId);
        int fromIndex = Math.min(position, mockCertificates.size());
        int toIndex = Math.min(fromIndex + size, mockCertificates.size());
        return new ArrayList<>(mockCertificates.subList(fromIndex, toIndex));
    }

    @Override
    public List<SectigoCertificateDto> fetchAllCertificates(String status, String correlationId) {
        log.info("MockSectigoClient: fetching all mock certificates status={}, correlationId={}", status, correlationId);
        return new ArrayList<>(mockCertificates);
    }

    @Override
    public Optional<SectigoCertificateDto> fetchCertificateById(String certificateId, String correlationId) {
        log.info("MockSectigoClient: fetching certificateById={}, correlationId={}", certificateId, correlationId);
        return mockCertificates.stream()
                .filter(c -> certificateId.equalsIgnoreCase(c.getId()))
                .findFirst();
    }

    @Override
    public byte[] downloadCertificateChain(String certificateId, String correlationId) {
        log.info("MockSectigoClient: downloading mock certificate chain for certId={}", certificateId);
        String mockPem = """
                -----BEGIN CERTIFICATE-----
                MIIE/TCCA+WgAwIBAgIUQSts...MOCK_SECTIGO_CERTIFICATE_PEM...
                -----END CERTIFICATE-----
                -----BEGIN CERTIFICATE-----
                MIIFdzCCA1+gAwIBAgIQ...MOCK_SECTIGO_INTERMEDIATE_CA_PEM...
                -----END CERTIFICATE-----
                """;
        return mockPem.getBytes(StandardCharsets.UTF_8);
    }
}
