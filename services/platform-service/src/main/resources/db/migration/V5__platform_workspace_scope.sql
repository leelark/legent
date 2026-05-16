-- V5: Add workspace scope to platform-owned records.
-- workspace_id stays nullable so existing tenant-global platform records remain readable by intentional
-- tenant-global flows; workspace-facing APIs require X-Workspace-Id and write workspace-scoped rows.

ALTER TABLE webhooks
ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);

ALTER TABLE webhook_logs
ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);

ALTER TABLE webhook_retries
ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);

ALTER TABLE notifications
ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);

ALTER TABLE search_index_docs
ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_webhooks_tenant_workspace
    ON webhooks(tenant_id, workspace_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_webhook_logs_tenant_workspace
    ON webhook_logs(tenant_id, workspace_id, webhook_id);

CREATE INDEX IF NOT EXISTS idx_webhook_retries_tenant_workspace
    ON webhook_retries(tenant_id, workspace_id, status);

CREATE INDEX IF NOT EXISTS idx_notifications_tenant_workspace_read
    ON notifications(tenant_id, workspace_id, user_id, is_read);

CREATE INDEX IF NOT EXISTS idx_search_index_tenant_workspace
    ON search_index_docs(tenant_id, workspace_id);

CREATE INDEX IF NOT EXISTS idx_search_index_workspace_entity
    ON search_index_docs(tenant_id, workspace_id, entity_type, entity_id);

COMMENT ON COLUMN webhooks.workspace_id IS
'Nullable only for tenant-global legacy/platform records; workspace webhook APIs require workspace context.';

COMMENT ON COLUMN webhook_logs.workspace_id IS
'Nullable only for tenant-global legacy/platform webhook deliveries; workspace deliveries carry workspace context.';

COMMENT ON COLUMN webhook_retries.workspace_id IS
'Nullable only for tenant-global legacy/platform retry records; workspace retries carry workspace context.';

COMMENT ON COLUMN notifications.workspace_id IS
'Nullable only for tenant-global platform notifications; workspace notification APIs require workspace context.';

COMMENT ON COLUMN search_index_docs.workspace_id IS
'Nullable only for tenant-global platform search docs; workspace search APIs require workspace context.';
