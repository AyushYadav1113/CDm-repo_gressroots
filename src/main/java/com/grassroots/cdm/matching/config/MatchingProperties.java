package com.grassroots.cdm.matching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configurable thresholds, weights, and heuristic parameters for the Certificate Matching Engine.
 * All thresholds are fully externalized to prevent arbitrary hardcoded production decisions.
 */
@Configuration
@ConfigurationProperties(prefix = "cdm.matching")
public class MatchingProperties {

    /**
     * Minimum score required for an automatic match without manual review.
     * Default: 0.85 (85%)
     */
    private double autoMatchThreshold = 0.85;

    /**
     * Minimum score required for a candidate to be considered a potential match flagged for manual review.
     * Scores below this threshold result in NO_MATCH.
     * Default: 0.60 (60%)
     */
    private double reviewThreshold = 0.60;

    /**
     * Maximum difference between the top candidate and the runner-up candidate to consider the match ambiguous.
     * If (topScore - secondScore) <= ambiguityMargin, the match is flagged as REVIEW_REQUIRED
     * regardless of whether topScore >= autoMatchThreshold.
     * Default: 0.05 (5%)
     */
    private double ambiguityMargin = 0.05;

    /**
     * Weight assigned to Subject Alternative Name (SAN) set similarity.
     * Default: 0.35
     */
    private double sanWeight = 0.35;

    /**
     * Weight assigned to Common Name (CN) match.
     * Default: 0.25
     */
    private double cnWeight = 0.25;

    /**
     * Weight assigned to explicit renewal linkage (e.g. Sectigo renewedFromCertificateId).
     * Default: 0.20
     */
    private double renewalLinkageWeight = 0.20;

    /**
     * Weight assigned to lifecycle validity extension (new validTo > old validTo).
     * Default: 0.10
     */
    private double lifecycleWeight = 0.10;

    /**
     * Weight assigned to CA Issuer authority continuity.
     * Default: 0.10
     */
    private double issuerWeight = 0.10;

    /**
     * Whether to strictly disqualify candidate certificates that are already expired.
     * Default: true
     */
    private boolean rejectExpiredCandidates = true;

    /**
     * Whether to require that the new candidate's validTo date extends beyond the old certificate's validTo.
     * Default: true
     */
    private boolean requireValidityExtension = true;

    /**
     * Whether wildcard certificate expansion (*.domain.com matching sub.domain.com) is permitted.
     * Default: true
     */
    private boolean allowWildcardExpansion = true;

    public double getAutoMatchThreshold() {
        return autoMatchThreshold;
    }

    public void setAutoMatchThreshold(double autoMatchThreshold) {
        this.autoMatchThreshold = autoMatchThreshold;
    }

    public double getReviewThreshold() {
        return reviewThreshold;
    }

    public void setReviewThreshold(double reviewThreshold) {
        this.reviewThreshold = reviewThreshold;
    }

    public double getAmbiguityMargin() {
        return ambiguityMargin;
    }

    public void setAmbiguityMargin(double ambiguityMargin) {
        this.ambiguityMargin = ambiguityMargin;
    }

    public double getSanWeight() {
        return sanWeight;
    }

    public void setSanWeight(double sanWeight) {
        this.sanWeight = sanWeight;
    }

    public double getCnWeight() {
        return cnWeight;
    }

    public void setCnWeight(double cnWeight) {
        this.cnWeight = cnWeight;
    }

    public double getRenewalLinkageWeight() {
        return renewalLinkageWeight;
    }

    public void setRenewalLinkageWeight(double renewalLinkageWeight) {
        this.renewalLinkageWeight = renewalLinkageWeight;
    }

    public double getLifecycleWeight() {
        return lifecycleWeight;
    }

    public void setLifecycleWeight(double lifecycleWeight) {
        this.lifecycleWeight = lifecycleWeight;
    }

    public double getIssuerWeight() {
        return issuerWeight;
    }

    public void setIssuerWeight(double issuerWeight) {
        this.issuerWeight = issuerWeight;
    }

    public boolean isRejectExpiredCandidates() {
        return rejectExpiredCandidates;
    }

    public void setRejectExpiredCandidates(boolean rejectExpiredCandidates) {
        this.rejectExpiredCandidates = rejectExpiredCandidates;
    }

    public boolean isRequireValidityExtension() {
        return requireValidityExtension;
    }

    public void setRequireValidityExtension(boolean requireValidityExtension) {
        this.requireValidityExtension = requireValidityExtension;
    }

    public boolean isAllowWildcardExpansion() {
        return allowWildcardExpansion;
    }

    public void setAllowWildcardExpansion(boolean allowWildcardExpansion) {
        this.allowWildcardExpansion = allowWildcardExpansion;
    }

    @Override
    public String toString() {
        return "MatchingProperties{" +
                "autoMatchThreshold=" + autoMatchThreshold +
                ", reviewThreshold=" + reviewThreshold +
                ", ambiguityMargin=" + ambiguityMargin +
                ", sanWeight=" + sanWeight +
                ", cnWeight=" + cnWeight +
                ", renewalLinkageWeight=" + renewalLinkageWeight +
                ", lifecycleWeight=" + lifecycleWeight +
                ", issuerWeight=" + issuerWeight +
                ", rejectExpiredCandidates=" + rejectExpiredCandidates +
                ", requireValidityExtension=" + requireValidityExtension +
                ", allowWildcardExpansion=" + allowWildcardExpansion +
                '}';
    }
}
