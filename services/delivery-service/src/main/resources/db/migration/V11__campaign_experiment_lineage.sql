-- V11: Campaign experiment lineage on delivery message logs.

ALTER TABLE message_logs
    ADD COLUMN IF NOT EXISTS experiment_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS variant_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS holdout BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cost_reserved NUMERIC(18,6) NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_message_logs_experiment_variant
    ON message_logs (tenant_id, workspace_id, campaign_id, experiment_id, variant_id)
    WHERE deleted_at IS NULL;
