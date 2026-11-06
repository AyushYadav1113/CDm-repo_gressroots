package com.grassroots.cdm.dto;

import com.grassroots.cdm.entity.AuditLogRecord;
import com.grassroots.cdm.entity.CertificateRecord;
import com.grassroots.cdm.entity.CertificateReplacement;
import com.grassroots.cdm.entity.DeploymentJob;
import com.grassroots.cdm.entity.MidServer;
import com.grassroots.cdm.entity.TargetServer;
import com.grassroots.cdm.entity.enums.MidServerStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapper component translating JPA entities to external REST DTO representations.
 * Ensures domain entities and sensitive fields (passwords, private keys, secrets) are never leaked.
 */
@Component
public class CdmDtoMapper {

    public CertificateSummaryDto toCertificateSummaryDto(CertificateRecord cert) {
        if (cert == null) {
            return null;
        }
        return new CertificateSummaryDto(
                cert.getId(),
                cert.getExternalId(),
                cert.getSerialNumber(),
                cert.getThumbprint(),
                cert.getCommonName(),
                cert.getSubjectAlternativeNames(),
                cert.getIssuer(),
                cert.getValidFrom(),
                cert.getValidTo(),
                cert.getSource() != null ? cert.getSource().name() : null,
                cert.getStatus() != null ? cert.getStatus().name() : null
        );
    }

    public TargetServerDto toTargetServerDto(TargetServer server) {
        if (server == null) {
            return null;
        }
        UUID midServerId = server.getMidServer() != null ? server.getMidServer().getId() : null;
        String midServerName = server.getMidServer() != null ? server.getMidServer().getName() : null;

        return new TargetServerDto(
                server.getId(),
                server.getHostname(),
                server.getIpAddress(),
                server.getOperatingSystem() != null ? server.getOperatingSystem().name() : null,
                server.getTechnology() != null ? server.getTechnology().name() : null,
                server.getEnvironment() != null ? server.getEnvironment().name() : null,
                midServerId,
                midServerName,
                server.getStatus() != null ? server.getStatus().name() : null
        );
    }

    public CertificateReplacementDto toCertificateReplacementDto(CertificateReplacement replacement) {
        if (replacement == null) {
            return null;
        }
        UUID oldCertId = replacement.getOldCertificate() != null ? replacement.getOldCertificate().getId() : null;
        String oldCn = replacement.getOldCertificate() != null ? replacement.getOldCertificate().getCommonName() : null;
        String oldTp = replacement.getOldCertificate() != null ? replacement.getOldCertificate().getThumbprint() : null;

        UUID newCertId = replacement.getNewCertificate() != null ? replacement.getNewCertificate().getId() : null;
        String newCn = replacement.getNewCertificate() != null ? replacement.getNewCertificate().getCommonName() : null;
        String newTp = replacement.getNewCertificate() != null ? replacement.getNewCertificate().getThumbprint() : null;

        return new CertificateReplacementDto(
                replacement.getId(),
                oldCertId,
                oldCn,
                oldTp,
                newCertId,
                newCn,
                newTp,
                replacement.getMatchingScore(),
                replacement.getMatchingReasons(),
                replacement.getMatchStatus() != null ? replacement.getMatchStatus().name() : null,
                replacement.getCreatedAt()
        );
    }

    public DeploymentJobDto toDeploymentJobDto(DeploymentJob job) {
        if (job == null) {
            return null;
        }
        UUID oldCertId = job.getOldCertificate() != null ? job.getOldCertificate().getId() : null;
        UUID newCertId = job.getNewCertificate() != null ? job.getNewCertificate().getId() : null;
        UUID targetServerId = job.getTargetServer() != null ? job.getTargetServer().getId() : null;

        return new DeploymentJobDto(
                job.getId(),
                job.getJobReference(),
                job.getIdempotencyKey(),
                oldCertId,
                newCertId,
                targetServerId,
                job.getTargetHost(),
                job.getTargetPort(),
                job.getTargetType(),
                job.getDeploymentType() != null ? job.getDeploymentType().name() : null,
                job.getStatus() != null ? job.getStatus().name() : null,
                job.getAttemptCount(),
                job.getMaxRetries(),
                job.getErrorInformation(),
                job.getMidServerTaskId(),
                job.getScheduledAt(),
                job.getStartedAt(),
                job.getCompletedAt()
        );
    }

    public AuditLogDto toAuditLogDto(AuditLogRecord record) {
        if (record == null) {
            return null;
        }
        return new AuditLogDto(
                record.getId(),
                record.getCorrelationId(),
                record.getReferenceId(),
                record.getEventType(),
                record.getMessage(),
                record.getEntityName(),
                record.getEntityId(),
                record.getActor(),
                record.getOutcome(),
                record.getDetails(),
                record.getClientIp(),
                record.getTimestamp()
        );
    }

    public MidServerDto toMidServerDto(MidServer server) {
        if (server == null) {
            return null;
        }
        return new MidServerDto(
                server.getId(),
                server.getName(),
                server.getEndpoint(),
                server.getStatus() != null ? server.getStatus().name() : null,
                server.getNetworkMetadata(),
                server.getHealthInfo()
        );
    }

    public MidServerHealthDto toMidServerHealthDto(MidServer server) {
        if (server == null) {
            return null;
        }
        boolean isUp = server.getStatus() == MidServerStatus.UP;
        return new MidServerHealthDto(
                server.getId(),
                server.getName(),
                server.getEndpoint(),
                server.getStatus() != null ? server.getStatus().name() : null,
                isUp,
                server.getHealthInfo(),
                server.getNetworkMetadata(),
                Instant.now()
        );
    }
}
