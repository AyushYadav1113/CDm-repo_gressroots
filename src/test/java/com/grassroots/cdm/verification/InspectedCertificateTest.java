package com.grassroots.cdm.verification;

import com.grassroots.cdm.verification.model.InspectedCertificate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InspectedCertificateTest {

    @Test
    @DisplayName("Hostname matching: exact match against DNS SANs and Common Name")
    void testExactHostnameMatching() {
        InspectedCertificate cert = new InspectedCertificate(
                null,
                "AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899",
                "11223344556677889900AABBCCDDEEFF00112233",
                "1A2B",
                "6699",
                "api.grassroots.local",
                "CN=api.grassroots.local, O=Grassroots",
                List.of("DNS:api.grassroots.local", "DNS:web.grassroots.local", "IP:127.0.0.1"),
                List.of("api.grassroots.local", "web.grassroots.local"),
                List.of("127.0.0.1"),
                "CN=Grassroots Root CA",
                "Grassroots Root CA",
                Instant.now().minus(10, ChronoUnit.DAYS),
                Instant.now().plus(350, ChronoUnit.DAYS),
                "SHA256withRSA",
                3
        );

        // Matching DNS SANs
        assertThat(cert.matchesHostname("api.grassroots.local", true)).isTrue();
        assertThat(cert.matchesHostname("API.GRASSROOTS.LOCAL", true)).isTrue();
        assertThat(cert.matchesHostname("web.grassroots.local", true)).isTrue();

        // Matching IP
        assertThat(cert.matchesHostname("127.0.0.1", true)).isTrue();

        // Non-matching
        assertThat(cert.matchesHostname("db.grassroots.local", true)).isFalse();
        assertThat(cert.matchesHostname("192.168.1.1", true)).isFalse();
        assertThat(cert.matchesHostname(null, true)).isFalse();
        assertThat(cert.matchesHostname("", true)).isFalse();
    }

    @Test
    @DisplayName("Wildcard matching: compliant with RFC 6125 rules")
    void testWildcardHostnameMatching() {
        InspectedCertificate cert = new InspectedCertificate(
                null,
                "AABBCCDDEEFF",
                "11223344",
                "10",
                "16",
                "*.grassroots.local",
                "CN=*.grassroots.local",
                List.of("DNS:*.grassroots.local"),
                List.of("*.grassroots.local"),
                List.of(),
                "CN=Grassroots CA",
                "Grassroots CA",
                Instant.now().minus(5, ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS),
                "SHA256withRSA",
                3
        );

        // Single-level subdomains should match
        assertThat(cert.matchesHostname("api.grassroots.local", true)).isTrue();
        assertThat(cert.matchesHostname("auth.grassroots.local", true)).isTrue();
        assertThat(cert.matchesHostname("API.GRASSROOTS.LOCAL", true)).isTrue();

        // Apex domain should NOT match
        assertThat(cert.matchesHostname("grassroots.local", true)).isFalse();

        // Multi-level nested subdomains should NOT match RFC 6125
        assertThat(cert.matchesHostname("sub.api.grassroots.local", true)).isFalse();

        // Different domain should NOT match
        assertThat(cert.matchesHostname("api.other.com", true)).isFalse();

        // If allowWildcard is false, wildcard should NOT match
        assertThat(cert.matchesHostname("api.grassroots.local", false)).isFalse();
    }

    @Test
    @DisplayName("Fingerprint matching: handles SHA-256 and SHA-1 normalization")
    void testFingerprintMatching() {
        InspectedCertificate cert = new InspectedCertificate(
                null,
                "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855",
                "DA39A3EE5E6B4B0D3255BFEF95601890AFD80709",
                "01",
                "1",
                "localhost",
                "CN=localhost",
                List.of(),
                List.of(),
                List.of(),
                "CN=CA",
                "CA",
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS),
                "SHA256withRSA",
                3
        );

        // SHA-256 uppercase and lowercase
        assertThat(cert.matchesFingerprint("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855")).isTrue();
        assertThat(cert.matchesFingerprint("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")).isTrue();

        // SHA-256 with colons
        assertThat(cert.matchesFingerprint("E3:B0:C4:42:98:FC:1C:14:9A:FB:F4:C8:99:6F:B9:24:27:AE:41:E4:64:9B:93:4C:A4:95:99:1B:78:52:B8:55")).isTrue();

        // SHA-1 with colons
        assertThat(cert.matchesFingerprint("DA:39:A3:EE:5E:6B:4B:0D:32:55:BF:EF:95:60:18:90:AF:D8:07:09")).isTrue();

        // Non-matching
        assertThat(cert.matchesFingerprint("0000000000000000000000000000000000000000000000000000000000000000")).isFalse();
        assertThat(cert.matchesFingerprint(null)).isFalse();
        assertThat(cert.matchesFingerprint("")).isFalse();
    }

    @Test
    @DisplayName("Serial number matching: handles hex, 0x prefix, and decimal")
    void testSerialNumberMatching() {
        InspectedCertificate cert = new InspectedCertificate(
                null,
                "THUMBPRINT",
                "THUMBPRINT1",
                "1A2B",
                "6699",
                "localhost",
                "CN=localhost",
                List.of(),
                List.of(),
                List.of(),
                "CN=CA",
                "CA",
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS),
                "SHA256withRSA",
                3
        );

        // Hex matches
        assertThat(cert.matchesSerialNumber("1A2B")).isTrue();
        assertThat(cert.matchesSerialNumber("1a2b")).isTrue();
        assertThat(cert.matchesSerialNumber("0x1A2B")).isTrue();
        assertThat(cert.matchesSerialNumber("1A:2B")).isTrue();

        // Decimal matches
        assertThat(cert.matchesSerialNumber("6699")).isTrue();

        // Non-matches
        assertThat(cert.matchesSerialNumber("9999")).isFalse();
        assertThat(cert.matchesSerialNumber(null)).isFalse();
    }

    @Test
    @DisplayName("Validity dates: accurately flags expired and premature certificates")
    void testValidityDates() {
        // Active cert
        InspectedCertificate activeCert = new InspectedCertificate(
                null, "T1", "T2", "1", "1", "localhost", "CN=localhost",
                List.of(), List.of(), List.of(), "CN=CA", "CA",
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS),
                "SHA256withRSA", 3
        );
        assertThat(activeCert.isExpired()).isFalse();
        assertThat(activeCert.isNotYetValid()).isFalse();
        assertThat(activeCert.isValidNow()).isTrue();

        // Expired cert
        InspectedCertificate expiredCert = new InspectedCertificate(
                null, "T1", "T2", "1", "1", "localhost", "CN=localhost",
                List.of(), List.of(), List.of(), "CN=CA", "CA",
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS),
                "SHA256withRSA", 3
        );
        assertThat(expiredCert.isExpired()).isTrue();
        assertThat(expiredCert.isNotYetValid()).isFalse();
        assertThat(expiredCert.isValidNow()).isFalse();

        // Premature cert (not yet valid)
        InspectedCertificate prematureCert = new InspectedCertificate(
                null, "T1", "T2", "1", "1", "localhost", "CN=localhost",
                List.of(), List.of(), List.of(), "CN=CA", "CA",
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS),
                "SHA256withRSA", 3
        );
        assertThat(prematureCert.isExpired()).isFalse();
        assertThat(prematureCert.isNotYetValid()).isTrue();
        assertThat(prematureCert.isValidNow()).isFalse();
    }
}
