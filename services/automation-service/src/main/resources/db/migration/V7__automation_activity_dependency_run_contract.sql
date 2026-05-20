-- V7: Automation Studio dependency metadata and bounded run trace contract.

ALTER TABLE automation_activities
    ADD COLUMN IF NOT EXISTS dependency_activity_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS failure_policy VARCHAR(32) NOT NULL DEFAULT 'STOP_ON_FAILURE';

ALTER TABLE automation_activity_runs
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS dependency_trace_json JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_automation_activity_runs_scope_activity_created
    ON automation_activity_runs (tenant_id, workspace_id, activity_id, created_at DESC);
