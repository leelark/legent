CREATE TABLE IF NOT EXISTS platform_event_idempotency (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    workspace_id VARCHAR(36),
    event_type VARCHAR(128) NOT NULL,
    event_id VARCHAR(128),
    idempotency_key VARCHAR(128),
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_platform_event_idempotency_key
        CHECK (event_id IS NOT NULL OR idempotency_key IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_platform_event_idem_event_workspace
    ON platform_event_idempotency (tenant_id, workspace_id, event_type, event_id)
    WHERE workspace_id IS NOT NULL AND event_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_platform_event_idem_event_global
    ON platform_event_idempotency (tenant_id, event_type, event_id)
    WHERE workspace_id IS NULL AND event_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_platform_event_idem_key_workspace
    ON platform_event_idempotency (tenant_id, workspace_id, event_type, idempotency_key)
    WHERE workspace_id IS NOT NULL AND idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_platform_event_idem_key_global
    ON platform_event_idempotency (tenant_id, event_type, idempotency_key)
    WHERE workspace_id IS NULL AND idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_platform_event_idem_processed
    ON platform_event_idempotency (tenant_id, workspace_id, processed_at DESC);
