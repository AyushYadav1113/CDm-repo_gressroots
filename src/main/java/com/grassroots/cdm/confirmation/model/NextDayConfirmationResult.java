package com.grassroots.cdm.confirmation.model;

import com.grassroots.cdm.confirmation.ConfirmationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable result model from a single next-day certificate confirmation check.
 *
 * <p>Carries the final determination alongside diagnostic evidence so that
 * audit records and the scheduler can act appropriately without re-querying.</p>
 */
public final class NextDayConfirmationResult {

    private final UUID jobId;
    private final String correlationId;
    private final ConfirmationStatus status;
    private final String targetHost;
    private final String expectedThumbprint;
    private final String observedThumbprint;
    private final String serviceNowCiSysId;
    private final String message;
    private final Instant checkedAt;

    private NextDayConfirmationResult(Builder b) {
        this.jobId             = b.jobId;
        this.correlationId     = b.correlationId;
        this.status            = b.status;
        this.targetHost        = b.targetHost;
        this.expectedThumbprint = b.expectedThumbprint;
        this.observedThumbprint = b.observedThumbprint;
        this.serviceNowCiSysId  = b.serviceNowCiSysId;
        this.message           = b.message;
        this.checkedAt         = b.checkedAt != null ? b.checkedAt : Instant.now();
    }

    // ─── Factory methods ──────────────────────────────────────────────────────

    public static NextDayConfirmationResult confirmed(UUID jobId, String correlationId,
                                                      String targetHost,
                                                      String expectedThumbprint,
                                                      String observedThumbprint,
                                                      String serviceNowCiSysId) {
        return new Builder(jobId, correlationId, ConfirmationStatus.CONFIRMED)
                .targetHost(targetHost)
                .expectedThumbprint(expectedThumbprint)
                .observedThumbprint(observedThumbprint)
                .serviceNowCiSysId(serviceNowCiSysId)
                .message(String.format("ServiceNow CMDB confirms new certificate on host %s (thumbprint=%s)",
                        targetHost, observedThumbprint))
                .build();
    }

    public static NextDayConfirmationResult failed(UUID jobId, String correlationId,
                                                   String targetHost,
                                                   String expectedThumbprint,
                                                   String observedThumbprint,
                                                   String reason) {
        return new Builder(jobId, correlationId, ConfirmationStatus.CONFIRMATION_FAILED)
                .targetHost(targetHost)
                .expectedThumbprint(expectedThumbprint)
                .observedThumbprint(observedThumbprint)
                .message(String.format("Certificate mismatch on host %s. Expected=%s, Observed=%s. Reason: %s",
                        targetHost, expectedThumbprint, observedThumbprint, reason))
                .build();
    }

    public static NextDayConfirmationResult discoveryUnavailable(UUID jobId, String correlationId,
                                                                  String targetHost,
                                                                  String reason) {
        return new Builder(jobId, correlationId, ConfirmationStatus.DISCOVERY_UNAVAILABLE)
                .targetHost(targetHost)
                .message(String.format("Discovery data unavailable for host %s: %s", targetHost, reason))
                .build();
    }

    public static NextDayConfirmationResult manualReview(UUID jobId, String correlationId,
                                                          String targetHost,
                                                          String reason) {
        return new Builder(jobId, correlationId, ConfirmationStatus.MANUAL_REVIEW)
                .targetHost(targetHost)
                .message(String.format("Manual review required for host %s: %s", targetHost, reason))
                .build();
    }

    public static NextDayConfirmationResult pendingConfirmation(UUID jobId, String correlationId,
                                                                 String targetHost) {
        return new Builder(jobId, correlationId, ConfirmationStatus.PENDING_CONFIRMATION)
                .targetHost(targetHost)
                .message(String.format("Confirmation pending for host %s — not yet processed", targetHost))
                .build();
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public UUID getJobId()                  { return jobId; }
    public String getCorrelationId()        { return correlationId; }
    public ConfirmationStatus getStatus()   { return status; }
    public String getTargetHost()           { return targetHost; }
    public String getExpectedThumbprint()   { return expectedThumbprint; }
    public String getObservedThumbprint()   { return observedThumbprint; }
    public String getServiceNowCiSysId()    { return serviceNowCiSysId; }
    public String getMessage()              { return message; }
    public Instant getCheckedAt()           { return checkedAt; }

    public boolean isConfirmed()            { return status == ConfirmationStatus.CONFIRMED; }
    public boolean isFailed()               { return status == ConfirmationStatus.CONFIRMATION_FAILED; }
    public boolean isUnavailable()          { return status == ConfirmationStatus.DISCOVERY_UNAVAILABLE; }
    public boolean requiresManualReview()   { return status == ConfirmationStatus.MANUAL_REVIEW; }

    @Override
    public String toString() {
        return "NextDayConfirmationResult{jobId=" + jobId
                + ", status=" + status
                + ", host=" + targetHost
                + ", message='" + message + "'}";
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    public static final class Builder {
        private final UUID jobId;
        private final String correlationId;
        private final ConfirmationStatus status;
        private String targetHost;
        private String expectedThumbprint;
        private String observedThumbprint;
        private String serviceNowCiSysId;
        private String message;
        private Instant checkedAt;

        public Builder(UUID jobId, String correlationId, ConfirmationStatus status) {
            this.jobId         = jobId;
            this.correlationId = correlationId;
            this.status        = status;
        }
        public Builder targetHost(String v)           { this.targetHost = v; return this; }
        public Builder expectedThumbprint(String v)   { this.expectedThumbprint = v; return this; }
        public Builder observedThumbprint(String v)   { this.observedThumbprint = v; return this; }
        public Builder serviceNowCiSysId(String v)    { this.serviceNowCiSysId = v; return this; }
        public Builder message(String v)              { this.message = v; return this; }
        public Builder checkedAt(Instant v)           { this.checkedAt = v; return this; }
        public NextDayConfirmationResult build()      { return new NextDayConfirmationResult(this); }
    }
}
