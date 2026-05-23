-- V18: Persist immutable send-governance policy snapshots used by delivery execution.

ALTER TABLE message_logs
    ADD COLUMN IF NOT EXISTS send_governance_policy_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS send_governance_policy_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS send_governance_policy_version BIGINT,
    ADD COLUMN IF NOT EXISTS send_governance_policy_snapshot_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS send_governance_policy_snapshot JSONB;

CREATE INDEX IF NOT EXISTS idx_message_logs_governance_policy
    ON message_logs (tenant_id, workspace_id, send_governance_policy_id, send_governance_policy_version)
    WHERE deleted_at IS NULL AND send_governance_policy_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_message_logs_governance_snapshot_hash
    ON message_logs (tenant_id, workspace_id, send_governance_policy_snapshot_hash)
    WHERE deleted_at IS NULL AND send_governance_policy_snapshot_hash IS NOT NULL;
