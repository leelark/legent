-- V9: Delivery workspace strict ownership, event idempotency, and lineage hardening.

-- Message-level lineage and workspace-scoped uniqueness
ALTER TABLE message_logs
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE',
    ADD COLUMN IF NOT EXISTS failure_class VARCHAR(64),
    ADD COLUMN IF NOT EXISTS from_email VARCHAR(320),
    ADD COLUMN IF NOT EXISTS from_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reply_to_email VARCHAR(320);

UPDATE message_logs
SET ownership_scope = 'WORKSPACE'
WHERE ownership_scope IS NULL OR ownership_scope = '';

DROP INDEX IF EXISTS uq_message_logs_tenant_msg;
CREATE UNIQUE INDEX IF NOT EXISTS uq_message_logs_tenant_workspace_msg
    ON message_logs (tenant_id, workspace_id, message_id)
    WHERE deleted_at IS NULL;

-- Health checks: workspace/team ownership
ALTER TABLE provider_health_checks
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE';

UPDATE provider_health_checks
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE provider_health_checks
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_provider_health_workspace
    ON provider_health_checks (tenant_id, workspace_id, check_timestamp DESC);

-- Health status: workspace/team ownership
ALTER TABLE provider_health_status
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE';

UPDATE provider_health_status
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE provider_health_status
    ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE provider_health_status
    DROP CONSTRAINT IF EXISTS uq_provider_health_status;
DROP INDEX IF EXISTS uq_provider_health_status;
CREATE UNIQUE INDEX IF NOT EXISTS uq_provider_health_status_tenant_workspace_provider
    ON provider_health_status (tenant_id, workspace_id, provider_id);

CREATE INDEX IF NOT EXISTS idx_provider_health_status_workspace
    ON provider_health_status (tenant_id, workspace_id, updated_at DESC);

-- Replay queue: ownership + diagnostic lineage
ALTER TABLE delivery_replay_queue
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE',
    ADD COLUMN IF NOT EXISTS provider_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS failure_class VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source_job_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source_batch_id VARCHAR(64);

UPDATE delivery_replay_queue
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE delivery_replay_queue
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_replay_workspace_status
    ON delivery_replay_queue (tenant_id, workspace_id, status, priority, scheduled_at);

-- Suppression signals: workspace/team ownership
ALTER TABLE suppression_signals
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE';

UPDATE suppression_signals
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE suppression_signals
    ALTER COLUMN workspace_id SET NOT NULL;

DROP INDEX IF EXISTS idx_suppressions_email;
CREATE INDEX IF NOT EXISTS idx_suppressions_tenant_workspace_email
    ON suppression_signals (tenant_id, workspace_id, email, type)
    WHERE deleted_at IS NULL;

-- Event idempotency ledger for delivery and replay consumers
CREATE TABLE IF NOT EXISTS delivery_event_idempotency (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_id VARCHAR(128),
    idempotency_key VARCHAR(128),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(36),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_delivery_event_idempotency_event
    ON delivery_event_idempotency (tenant_id, workspace_id, event_type, event_id)
    WHERE event_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_delivery_event_idempotency_key
    ON delivery_event_idempotency (tenant_id, workspace_id, event_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_event_idempotency_processed
    ON delivery_event_idempotency (tenant_id, workspace_id, processed_at DESC);
