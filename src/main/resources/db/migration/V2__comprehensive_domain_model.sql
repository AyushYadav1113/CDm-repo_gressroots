-- ==============================================================================
-- CDM Database Migration: V2
-- Description: Comprehensive Domain Model for Certificate Deployment Manager
-- Entities: Certificates, Target Servers, Certificate Installations,
--           Certificate Replacements, Deployment Jobs, MID Servers, Audit Logs
-- ==============================================================================

-- 1. ENHANCE certificates TABLE
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS thumbprint VARCHAR(128);
UPDATE certificates SET thumbprint = fingerprint_sha256 WHERE thumbprint IS NULL;
ALTER TABLE certificates ALTER COLUMN thumbprint SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_cert_thumbprint ON certificates(thumbprint);
CREATE INDEX IF NOT EXISTS idx_cert_status ON certificates(status);
CREATE INDEX IF NOT EXISTS idx_cert_source ON certificates(source);

-- 2. CREATE TABLE: mid_servers
-- ServiceNow MID Server execution nodes handling target server communication
CREATE TABLE IF NOT EXISTS mid_servers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    endpoint VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'UP', -- UP, DOWN, DEGRADED, PAUSED, MAINTENANCE
    network_metadata JSONB,
    health_info JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_mid_server_status ON mid_servers(status);

-- 3. CREATE TABLE: target_servers
-- Managed infrastructure servers (IIS, Apache, Nginx, Java Keystores)
CREATE TABLE IF NOT EXISTS target_servers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hostname VARCHAR(255) NOT NULL UNIQUE,
    ip_address VARCHAR(45),
    operating_system VARCHAR(50) NOT NULL, -- WINDOWS_SERVER, LINUX_RHEL, LINUX_UBUNTU, etc.
    technology VARCHAR(50) NOT NULL, -- IIS, APACHE, NGINX, TOMCAT, JAVA_KEYSTORE, etc.
    environment VARCHAR(50) NOT NULL, -- PRODUCTION, STAGING, QA, DEVELOPMENT
    mid_server_id UUID REFERENCES mid_servers(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, MAINTENANCE, DECOMMISSIONED, UNREACHABLE
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_target_servers_mid_server ON target_servers(mid_server_id);
CREATE INDEX IF NOT EXISTS idx_target_servers_status ON target_servers(status);
CREATE INDEX IF NOT EXISTS idx_target_servers_env ON target_servers(environment);
CREATE INDEX IF NOT EXISTS idx_target_servers_tech ON target_servers(technology);

-- 4. CREATE TABLE: certificate_installations
-- Represents an active certificate bound to a specific server, port, and application binding
CREATE TABLE IF NOT EXISTS certificate_installations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    certificate_id UUID NOT NULL REFERENCES certificates(id) ON DELETE CASCADE,
    server_id UUID NOT NULL REFERENCES target_servers(id) ON DELETE CASCADE,
    technology VARCHAR(50) NOT NULL, -- IIS, APACHE, NGINX, TOMCAT, JAVA_KEYSTORE
    binding_info VARCHAR(255), -- e.g. IIS site name, Apache VirtualHost, Keystore alias
    installation_path VARCHAR(500), -- e.g. /etc/ssl/certs/app.crt, C:\certs\app.pfx
    port INT NOT NULL DEFAULT 443,
    status VARCHAR(50) NOT NULL DEFAULT 'INSTALLED', -- INSTALLED, PENDING_VERIFICATION, VERIFIED, FAILED, ORPHANED
    last_verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_cert_installation UNIQUE (server_id, port, binding_info)
);

CREATE INDEX IF NOT EXISTS idx_cert_install_cert_id ON certificate_installations(certificate_id);
CREATE INDEX IF NOT EXISTS idx_cert_install_server_id ON certificate_installations(server_id);
CREATE INDEX IF NOT EXISTS idx_cert_install_status ON certificate_installations(status);

-- 5. CREATE TABLE: certificate_replacements
-- Tracks matching relationship between expiring certificate and Sectigo renewal candidate
CREATE TABLE IF NOT EXISTS certificate_replacements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    old_certificate_id UUID NOT NULL REFERENCES certificates(id) ON DELETE CASCADE,
    new_certificate_id UUID NOT NULL REFERENCES certificates(id) ON DELETE CASCADE,
    matching_score DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    matching_reasons JSONB,
    match_status VARCHAR(50) NOT NULL DEFAULT 'AUTO_MATCHED', -- AUTO_MATCHED, MANUALLY_CONFIRMED, PENDING_REVIEW, REJECTED, SUPERSEDED
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_cert_replacement_pair UNIQUE (old_certificate_id, new_certificate_id)
);

CREATE INDEX IF NOT EXISTS idx_cert_repl_old_cert ON certificate_replacements(old_certificate_id);
CREATE INDEX IF NOT EXISTS idx_cert_repl_new_cert ON certificate_replacements(new_certificate_id);
CREATE INDEX IF NOT EXISTS idx_cert_repl_status ON certificate_replacements(match_status);

-- 6. ENHANCE deployment_jobs TABLE
ALTER TABLE deployment_jobs ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);
ALTER TABLE deployment_jobs ADD COLUMN IF NOT EXISTS target_server_id UUID REFERENCES target_servers(id) ON DELETE RESTRICT;
ALTER TABLE deployment_jobs ADD COLUMN IF NOT EXISTS installation_id UUID REFERENCES certificate_installations(id) ON DELETE SET NULL;
ALTER TABLE deployment_jobs ADD COLUMN IF NOT EXISTS deployment_type VARCHAR(50) DEFAULT 'RENEWAL_REPLACEMENT';
ALTER TABLE deployment_jobs ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE deployment_jobs ADD COLUMN IF NOT EXISTS started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE deployment_jobs ADD COLUMN IF NOT EXISTS error_information TEXT;
ALTER TABLE deployment_jobs ALTER COLUMN target_type DROP NOT NULL;
ALTER TABLE deployment_jobs ALTER COLUMN target_host DROP NOT NULL;
ALTER TABLE deployment_jobs ALTER COLUMN target_port DROP NOT NULL;

-- Generate unique idempotency keys for existing jobs if any
UPDATE deployment_jobs SET idempotency_key = job_reference WHERE idempotency_key IS NULL;
ALTER TABLE deployment_jobs ALTER COLUMN idempotency_key SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_jobs_idempotency_key ON deployment_jobs(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_jobs_target_server ON deployment_jobs(target_server_id);
CREATE INDEX IF NOT EXISTS idx_jobs_installation ON deployment_jobs(installation_id);
CREATE INDEX IF NOT EXISTS idx_jobs_deployment_type ON deployment_jobs(deployment_type);
CREATE INDEX IF NOT EXISTS idx_jobs_scheduled_at ON deployment_jobs(scheduled_at);

-- 7. ENHANCE audit_logs TABLE
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(64);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS reference_id VARCHAR(100);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS event_type VARCHAR(100);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS message TEXT;

UPDATE audit_logs SET correlation_id = gen_random_uuid()::text WHERE correlation_id IS NULL;
UPDATE audit_logs SET event_type = action WHERE event_type IS NULL;
UPDATE audit_logs SET message = action || ' on ' || entity_name WHERE message IS NULL;

ALTER TABLE audit_logs ALTER COLUMN correlation_id SET NOT NULL;
ALTER TABLE audit_logs ALTER COLUMN event_type SET NOT NULL;
ALTER TABLE audit_logs ALTER COLUMN message SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_correlation_id ON audit_logs(correlation_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON audit_logs(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_reference_id ON audit_logs(reference_id);
