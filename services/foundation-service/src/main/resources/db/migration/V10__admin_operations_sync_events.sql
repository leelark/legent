-- V10: Admin operations synchronization ledger

CREATE TABLE IF NOT EXISTS admin_sync_events (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26),
    environment_id      VARCHAR(26),
    event_type          VARCHAR(128) NOT NULL,
    source_module       VARCHAR(64) NOT NULL,
    target_modules      JSONB NOT NULL DEFAULT '[]'::jsonb,
    payload             JSONB NOT NULL DEFAULT '{}'::jsonb,
    status              VARCHAR(32) NOT NULL DEFAULT 'APPLIED',
    applied_at          TIMESTAMPTZ,
    failure_reason      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_admin_sync_events_tenant
    ON admin_sync_events(tenant_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_admin_sync_events_status
    ON admin_sync_events(tenant_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_admin_sync_events_workspace
    ON admin_sync_events(tenant_id, workspace_id, created_at DESC)
    WHERE workspace_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_admin_sync_events_type
    ON admin_sync_events(tenant_id, event_type, created_at DESC)
    WHERE deleted_at IS NULL;
