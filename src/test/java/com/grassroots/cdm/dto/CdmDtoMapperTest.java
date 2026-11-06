package com.grassroots.cdm.dto;

import com.grassroots.cdm.deployment.DeploymentJobStatus;
import com.grassroots.cdm.entity.AuditLogRecord;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.CertificateReplacement;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.CertificateSource;
import com.grassroots.cdm.entity.enums.CertificateStatus;
import com.grassroots.cdm.entity.enums.DeploymentType;
import com.grassroots.cdm.entity.enums.EnvironmentType;
import com.grassroots.cdm.entity.enums.MatchStatus;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import com.grassroots.cdm.entity.enums.ServerOperatingSystem;
import com.grassroots.cdm.entity.enums.ServerStatus;
import com.grassroots.cdm.entity.enums.ServerTechnology;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CdmDtoMapperTest {

    private CdmDtoMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CdmDtoMapper();
    }

    @Test
    @DisplayName("toCertificateSummaryDto maps all non-sensitive fields and handles null input")
    void toCertificateSummaryDto_mapsCorrectly() {
        assertThat(mapper.toCertificateSummaryDto(null)).isNull();

        CertificateRecord cert = new CertificateRecord();
        cert.setId(UUID.randomUUID());
        cert.setExternalId("EXT-123");
        cert.setSerialNumber("SN-456");
        cert.setThumbprint("THUMB-789");
        cert.setCommonName("app.internal");
        cert.setSubjectAlternativeNames("DNS:app.internal");
        cert.setIssuer("DigiCert");
        cert.setValidFrom(Instant.now().minusSeconds(100));
        cert.setValidTo(Instant.now().plusSeconds(1000));
        cert.setSource(CertificateSource.SERVICENOW);
        cert.setStatus(CertificateStatus.ACTIVE);

        CertificateSummaryDto dto = mapper.toCertificateSummaryDto(cert);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(cert.getId());
        assertThat(dto.commonName()).isEqualTo("app.internal");
        assertThat(dto.source()).isEqualTo("SERVICENOW");
        assertThat(dto.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("toTargetServerDto maps target server with MID server relation")
    void toTargetServerDto_mapsCorrectly() {
        assertThat(mapper.toTargetServerDto(null)).isNull();

        MidServer mid = new MidServer("mid-01", "https://mid:8443", MidServerStatus.UP);
        mid.setId(UUID.randomUUID());

        TargetServer server = new TargetServer("web-01", "10.0.0.1", ServerOperatingSystem.LINUX_RHEL, ServerTechnology.NGINX, EnvironmentType.PRODUCTION);
        server.setId(UUID.randomUUID());
        server.setMidServer(mid);
        server.setStatus(ServerStatus.ACTIVE);

        TargetServerDto dto = mapper.toTargetServerDto(server);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(server.getId());
        assertThat(dto.hostname()).isEqualTo("web-01");
        assertThat(dto.midServerId()).isEqualTo(mid.getId());
        assertThat(dto.midServerName()).isEqualTo("mid-01");
    }

    @Test
    @DisplayName("toCertificateReplacementDto maps old and new certificate links")
    void toCertificateReplacementDto_mapsCorrectly() {
        assertThat(mapper.toCertificateReplacementDto(null)).isNull();

        CertificateRecord oldCert = new CertificateRecord();
        oldCert.setId(UUID.randomUUID());
        oldCert.setCommonName("old.corp");
        oldCert.setThumbprint("OLDTHUMB");

        CertificateRecord newCert = new CertificateRecord();
        newCert.setId(UUID.randomUUID());
        newCert.setCommonName("new.corp");
        newCert.setThumbprint("NEWTHUMB");

        CertificateReplacement rep = new CertificateReplacement(oldCert, newCert, 0.99, "[\"CN_MATCH\"]", MatchStatus.AUTO_MATCHED);
        rep.setId(UUID.randomUUID());

        CertificateReplacementDto dto = mapper.toCertificateReplacementDto(rep);

        assertThat(dto).isNotNull();
        assertThat(dto.oldCertificateId()).isEqualTo(oldCert.getId());
        assertThat(dto.oldCommonName()).isEqualTo("old.corp");
        assertThat(dto.newCertificateId()).isEqualTo(newCert.getId());
        assertThat(dto.newCommonName()).isEqualTo("new.corp");
        assertThat(dto.matchingScore()).isEqualTo(0.99);
    }

    @Test
    @DisplayName("toDeploymentJobDto maps deployment job fields without secrets")
    void toDeploymentJobDto_mapsCorrectly() {
        assertThat(mapper.toDeploymentJobDto(null)).isNull();

        DeploymentJob job = new DeploymentJob();
        job.setId(UUID.randomUUID());
        job.setJobReference("JOB-REF-01");
        job.setIdempotencyKey("IDEM-01");
        job.setTargetHost("host.corp");
        job.setTargetPort(8443);
        job.setTargetType("NGINX");
        job.setDeploymentType(DeploymentType.RENEWAL_REPLACEMENT);
        job.setStatus(DeploymentJobStatus.VERIFIED);
        job.setAttemptCount(1);
        job.setMaxRetries(3);

        DeploymentJobDto dto = mapper.toDeploymentJobDto(job);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(job.getId());
        assertThat(dto.jobReference()).isEqualTo("JOB-REF-01");
        assertThat(dto.status()).isEqualTo("VERIFIED");
        assertThat(dto.targetHost()).isEqualTo("host.corp");
    }

    @Test
    @DisplayName("toAuditLogDto maps audit record fields")
    void toAuditLogDto_mapsCorrectly() {
        assertThat(mapper.toAuditLogDto(null)).isNull();

        AuditLogRecord record = new AuditLogRecord("c1", "r1", "ACTION_RUN", "msg", "Entity", "id1", "admin", "SUCCESS", "{}", "127.0.0.1");
        record.setId(UUID.randomUUID());

        AuditLogDto dto = mapper.toAuditLogDto(record);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(record.getId());
        assertThat(dto.correlationId()).isEqualTo("c1");
        assertThat(dto.outcome()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("toMidServerDto and toMidServerHealthDto map MID server and health diagnostics")
    void toMidServerDto_and_HealthDto_mapsCorrectly() {
        assertThat(mapper.toMidServerDto(null)).isNull();
        assertThat(mapper.toMidServerHealthDto(null)).isNull();

        MidServer mid = new MidServer("mid-prod", "https://mid:8443", MidServerStatus.UP);
        mid.setId(UUID.randomUUID());
        mid.setHealthInfo("{\"cpu\":10}");

        MidServerDto dto = mapper.toMidServerDto(mid);
        assertThat(dto.name()).isEqualTo("mid-prod");
        assertThat(dto.status()).isEqualTo("UP");

        MidServerHealthDto healthDto = mapper.toMidServerHealthDto(mid);
        assertThat(healthDto.name()).isEqualTo("mid-prod");
        assertThat(healthDto.healthy()).isTrue();
        assertThat(healthDto.healthInfo()).isEqualTo("{\"cpu\":10}");
    }
}
