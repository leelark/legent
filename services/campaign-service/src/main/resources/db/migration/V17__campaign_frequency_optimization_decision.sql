-- V17: Campaign-owned approved frequency optimization decision evidence.

ALTER TABLE campaign_frequency_policies
    ADD COLUMN IF NOT EXISTS optimization_policy_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS optimization_run_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS optimization_snapshot_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS optimization_recommended_max_sends INTEGER,
    ADD COLUMN IF NOT EXISTS optimization_approved BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS optimization_approved_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_campaign_frequency_optimization_run
    ON campaign_frequency_policies (tenant_id, workspace_id, optimization_run_id)
    WHERE optimization_run_id IS NOT NULL
      AND deleted_at IS NULL;
