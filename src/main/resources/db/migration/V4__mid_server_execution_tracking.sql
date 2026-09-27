-- ==============================================================================
-- CDM Database Migration: V4
-- Description: Add mid_server_executions table for execution status persistence
-- ==============================================================================

CREATE TABLE IF NOT EXISTS mid_server_executions (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES deployment_jobs(id) ON DELETE CASCADE,
    mid_server_id UUID REFERENCES mid_servers(id) ON DELETE SET NULL,
    task_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(50) NOT NULL,
    exit_code INT,
    stdout_summary TEXT,
    stderr_summary TEXT,
    error_message TEXT,
    dispatched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_mid_exec_job_id ON mid_server_executions(job_id);
CREATE INDEX IF NOT EXISTS idx_mid_exec_task_id ON mid_server_executions(task_id);
CREATE INDEX IF NOT EXISTS idx_mid_exec_idempotency_key ON mid_server_executions(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_mid_exec_status ON mid_server_executions(status);
