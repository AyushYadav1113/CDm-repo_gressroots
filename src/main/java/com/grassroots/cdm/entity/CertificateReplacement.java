package com.grassroots.cdm.entity;

import com.grassroots.cdm.entity.enums.MatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entity representing correlation and replacement relationships between expiring and renewed certificates.
 */
@Entity
@Table(
        name = "certificate_replacements",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_cert_replacement_pair", columnNames = {"old_certificate_id", "new_certificate_id"})
        }
)
public class CertificateReplacement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "old_certificate_id", nullable = false)
    private CertificateRecord oldCertificate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_certificate_id", nullable = false)
    private CertificateRecord newCertificate;

    @Column(name = "matching_score", nullable = false)
    private double matchingScore = 1.0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matching_reasons", columnDefinition = "JSONB")
    private String matchingReasons;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 50)
    private MatchStatus matchStatus = MatchStatus.AUTO_MATCHED;

    public CertificateReplacement() {
    }

    public CertificateReplacement(CertificateRecord oldCertificate, CertificateRecord newCertificate,
                                  double matchingScore, String matchingReasons, MatchStatus matchStatus) {
        this.oldCertificate = oldCertificate;
        this.newCertificate = newCertificate;
        this.matchingScore = matchingScore;
        this.matchingReasons = matchingReasons;
        this.matchStatus = matchStatus;
    }

    public CertificateRecord getOldCertificate() {
        return oldCertificate;
    }

    public void setOldCertificate(CertificateRecord oldCertificate) {
        this.oldCertificate = oldCertificate;
    }

    public CertificateRecord getNewCertificate() {
        return newCertificate;
    }

    public void setNewCertificate(CertificateRecord newCertificate) {
        this.newCertificate = newCertificate;
    }

    public double getMatchingScore() {
        return matchingScore;
    }

    public void setMatchingScore(double matchingScore) {
        this.matchingScore = matchingScore;
    }

    public String getMatchingReasons() {
        return matchingReasons;
    }

    public void setMatchingReasons(String matchingReasons) {
        this.matchingReasons = matchingReasons;
    }

    public MatchStatus getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(MatchStatus matchStatus) {
        this.matchStatus = matchStatus;
    }
}
