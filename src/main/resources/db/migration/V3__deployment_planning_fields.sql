-- ==============================================================================
-- CDM Database Migration: V3
-- Description: Add priority and creation_reason to deployment_jobs for Deployment Planning
-- ==============================================================================

ALTER TABLE deployment_jobs ADD COLUMN IF NOT EXISTS priority VARCHAR(50) DEFAULT 'NORMAL';
ALTER TABLE deployment_jobs ADD COLUMN IF NOT EXISTS creation_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_jobs_priority ON deployment_jobs(priority);
