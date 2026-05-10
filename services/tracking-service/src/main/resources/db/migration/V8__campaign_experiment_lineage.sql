-- V8: Campaign experiment lineage for analytics and winner evaluation.

ALTER TABLE raw_events
    ADD COLUMN IF NOT EXISTS experiment_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS variant_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS holdout BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_raw_events_experiment_variant
    ON raw_events (tenant_id, workspace_id, campaign_id, experiment_id, variant_id, event_type, "timestamp" DESC);
