-- V8: Add campaign reconciliation keys and workspace ownership for delivery feedback.

ALTER TABLE message_logs
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS job_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS batch_id VARCHAR(64);

UPDATE message_logs
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE message_logs
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_message_logs_tenant_workspace
    ON message_logs (tenant_id, workspace_id);

CREATE INDEX IF NOT EXISTS idx_message_logs_tenant_workspace_campaign
    ON message_logs (tenant_id, workspace_id, campaign_id);

CREATE INDEX IF NOT EXISTS idx_message_logs_tenant_workspace_job
    ON message_logs (tenant_id, workspace_id, job_id);
