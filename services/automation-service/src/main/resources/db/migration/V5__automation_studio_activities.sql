-- V5: Automation Studio durable activities and run history.

CREATE TABLE IF NOT EXISTS automation_activities (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(26) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    activity_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    schedule_expression VARCHAR(128),
    input_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    verification_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_run_at TIMESTAMPTZ,
    next_run_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_automation_activity_name UNIQUE (tenant_id, workspace_id, name)
);

CREATE INDEX IF NOT EXISTS idx_automation_activities_scope
    ON automation_activities (tenant_id, workspace_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS automation_activity_runs (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(26) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    activity_id VARCHAR(26) NOT NULL REFERENCES automation_activities(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    dry_run BOOLEAN NOT NULL DEFAULT TRUE,
    trigger_source VARCHAR(64),
    rows_read BIGINT DEFAULT 0,
    rows_written BIGINT DEFAULT 0,
    error_message TEXT,
    result_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_automation_activity_runs_scope
    ON automation_activity_runs (tenant_id, workspace_id, activity_id, created_at DESC);
