-- V12: Journey path and conversion-goal lineage for tracking analytics.

ALTER TABLE raw_events
    ADD COLUMN IF NOT EXISTS experiment_scope VARCHAR(32),
    ADD COLUMN IF NOT EXISTS workflow_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS workflow_version INT,
    ADD COLUMN IF NOT EXISTS workflow_run_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS step_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS path_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS goal_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_raw_events_journey_goal
    ON raw_events (tenant_id, workspace_id, workflow_id, goal_id, event_type, "timestamp" DESC)
    WHERE workflow_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_raw_events_journey_path
    ON raw_events (tenant_id, workspace_id, workflow_id, path_id, step_id, event_type, "timestamp" DESC)
    WHERE workflow_id IS NOT NULL;
