-- V19: Scope subscriber-key uniqueness to workspace for active subscribers.

ALTER TABLE subscribers DROP CONSTRAINT IF EXISTS uq_subscriber_tenant_key;
ALTER TABLE subscribers DROP CONSTRAINT IF EXISTS uk_subscriber_tenant_key;

DROP INDEX IF EXISTS uq_subscriber_tenant_key;
DROP INDEX IF EXISTS uk_subscriber_tenant_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriber_tenant_workspace_key_active
    ON subscribers (tenant_id, workspace_id, subscriber_key)
    WHERE deleted_at IS NULL;
