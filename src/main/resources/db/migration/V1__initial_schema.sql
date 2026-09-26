-- ==============================================================================
-- CDM Database Initial Migration
-- Version: V1
-- Description: Initial schema for Certificate Deployment Manager (CDM)
-- ==============================================================================

-- Enable UUID extension if supported
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Table: certificates
-- Stores discovered and renewed certificate metadata
CREATE TABLE IF NOT EXISTS certificates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    serial_number VARCHAR(128) NOT NULL,
    common_name VARCHAR(255) NOT NULL,
    subject_alternative_names TEXT,
    issuer VARCHAR(255),
    fingerprint_sha256 VARCHAR(128) NOT NULL UNIQUE,
    valid_from TIMESTAMP WITH TIME ZONE,
    valid_to TIMESTAMP WITH TIME ZONE,
    source VARCHAR(50) NOT NULL, -- e.g. SERVICENOW, SECTIGO
    external_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_cert_common_name ON certificates(common_name);
CREATE INDEX IF NOT EXISTS idx_cert_valid_to ON certificates(valid_to);
CREATE INDEX IF NOT EXISTS idx_cert_serial_number ON certificates(serial_number);

-- Table: deployment_jobs
-- Tracks the end-to-end lifecycle of certificate deployment orchestration
CREATE TABLE IF NOT EXISTS deployment_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_reference VARCHAR(64) NOT NULL UNIQUE,
    old_certificate_id UUID REFERENCES certificates(id) ON DELETE SET NULL,
    new_certificate_id UUID REFERENCES certificates(id) ON DELETE SET NULL,
    target_host VARCHAR(255) NOT NULL,
    target_port INT NOT NULL DEFAULT 443,
    target_type VARCHAR(50) NOT NULL, -- WINDOWS_IIS, LINUX_APACHE, LINUX_NGINX, JAVA_KEYSTORE
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    error_message TEXT,
    mid_server_task_id VARCHAR(255),
    scheduled_at TIMESTAMP WITH TIME ZONE,
    dispatched_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_jobs_status ON deployment_jobs(status);
CREATE INDEX IF NOT EXISTS idx_jobs_target_host ON deployment_jobs(target_host);

-- Table: audit_logs
-- Immutable tamper-evident audit trail for all orchestration and security events
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action VARCHAR(100) NOT NULL,
    entity_name VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255),
    actor VARCHAR(100) NOT NULL,
    outcome VARCHAR(50) NOT NULL, -- SUCCESS, FAILURE, REJECTED
    details JSONB,
    client_ip VARCHAR(50),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_logs(entity_name, entity_id);
