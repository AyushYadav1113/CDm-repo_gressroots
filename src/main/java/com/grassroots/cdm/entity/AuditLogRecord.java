package com.grassroots.cdm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit log record capturing all system state changes, deployment operations, and security events.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLogRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "entity_name", nullable = false, length = 100)
    private String entityName;

    @Column(name = "entity_id", length = 255)
    private String entityId;

    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @Column(name = "outcome", nullable = false, length = 50)
    private String outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "JSONB")
    private String details;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp = Instant.now();

    public AuditLogRecord() {
    }

    public AuditLogRecord(String correlationId, String referenceId, String eventType, String message,
                          String entityName, String entityId, String actor, String outcome,
                          String details, String clientIp) {
        this.correlationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        this.referenceId = referenceId;
        this.eventType = eventType;
        this.action = eventType;
        this.message = message;
        this.entityName = entityName;
        this.entityId = entityId;
        this.actor = actor;
        this.outcome = outcome;
        this.details = details;
        this.clientIp = clientIp;
        this.timestamp = Instant.now();
    }

    public AuditLogRecord(String action, String entityName, String entityId, String actor,
                          String outcome, String details, String clientIp) {
        this(UUID.randomUUID().toString(), entityId, action, action + " on " + entityName,
                entityName, entityId, actor, outcome, details, clientIp);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
        if (this.action == null) {
            this.action = eventType;
        }
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
        if (this.eventType == null) {
            this.eventType = action;
        }
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditLogRecord that = (AuditLogRecord) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
