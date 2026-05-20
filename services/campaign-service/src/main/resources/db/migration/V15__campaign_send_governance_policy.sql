-- V15: Persist campaign-level send governance policy selection.

ALTER TABLE campaigns
    ADD COLUMN IF NOT EXISTS send_governance_policy_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_campaign_send_governance_policy
    ON campaigns (tenant_id, workspace_id, send_governance_policy_id)
    WHERE deleted_at IS NULL AND send_governance_policy_id IS NOT NULL;
