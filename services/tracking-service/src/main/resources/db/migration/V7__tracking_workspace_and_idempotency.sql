-- V7: Workspace strict analytics scope + event idempotency ledger.

ALTER TABLE raw_events
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32);

UPDATE raw_events
SET workspace_id = COALESCE(NULLIF(workspace_id, ''), 'workspace-default'),
    ownership_scope = COALESCE(NULLIF(ownership_scope, ''), 'WORKSPACE')
WHERE workspace_id IS NULL
   OR workspace_id = ''
   OR ownership_scope IS NULL
   OR ownership_scope = '';

ALTER TABLE raw_events
    ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE raw_events
    ALTER COLUMN ownership_scope SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_raw_events_tenant_workspace_time
    ON raw_events(tenant_id, workspace_id, "timestamp" DESC);

CREATE INDEX IF NOT EXISTS idx_raw_events_workspace_message
    ON raw_events(tenant_id, workspace_id, message_id, subscriber_id, event_type, "timestamp" DESC);

ALTER TABLE campaign_summaries
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32);

UPDATE campaign_summaries
SET workspace_id = COALESCE(NULLIF(workspace_id, ''), 'workspace-default'),
    ownership_scope = COALESCE(NULLIF(ownership_scope, ''), 'WORKSPACE')
WHERE workspace_id IS NULL
   OR workspace_id = ''
   OR ownership_scope IS NULL
   OR ownership_scope = '';

ALTER TABLE campaign_summaries
    ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE campaign_summaries
    ALTER COLUMN ownership_scope SET NOT NULL;

ALTER TABLE subscriber_summaries
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32);

UPDATE subscriber_summaries
SET workspace_id = COALESCE(NULLIF(workspace_id, ''), 'workspace-default'),
    ownership_scope = COALESCE(NULLIF(ownership_scope, ''), 'WORKSPACE')
WHERE workspace_id IS NULL
   OR workspace_id = ''
   OR ownership_scope IS NULL
   OR ownership_scope = '';

ALTER TABLE subscriber_summaries
    ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE subscriber_summaries
    ALTER COLUMN ownership_scope SET NOT NULL;

DROP INDEX IF EXISTS uq_camp_summaries_tenant_camp;
CREATE UNIQUE INDEX IF NOT EXISTS uq_camp_summaries_tenant_workspace_camp
    ON campaign_summaries(tenant_id, workspace_id, campaign_id)
    WHERE deleted_at IS NULL;

DROP INDEX IF EXISTS uq_sub_summaries_tenant_sub;
CREATE UNIQUE INDEX IF NOT EXISTS uq_sub_summaries_tenant_workspace_sub
    ON subscriber_summaries(tenant_id, workspace_id, subscriber_id)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS tracking_event_idempotency (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    workspace_id    VARCHAR(64) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    event_id        VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128),
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tracking_event_idempotency_event
    ON tracking_event_idempotency (tenant_id, workspace_id, event_type, event_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tracking_event_idempotency_key
    ON tracking_event_idempotency (tenant_id, workspace_id, event_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tracking_event_idempotency_processed
    ON tracking_event_idempotency (tenant_id, workspace_id, processed_at DESC);

