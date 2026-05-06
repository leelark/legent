-- V11: Workspace ownership, lifecycle enrichment, and event idempotency

-- Campaign ownership + lifecycle enrichments
ALTER TABLE campaigns
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE',
    ADD COLUMN IF NOT EXISTS sender_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS sender_email VARCHAR(320),
    ADD COLUMN IF NOT EXISTS reply_to_email VARCHAR(320),
    ADD COLUMN IF NOT EXISTS brand_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS tracking_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS compliance_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS provider_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS sending_domain VARCHAR(255),
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    ADD COLUMN IF NOT EXISTS quiet_hours_start TIME,
    ADD COLUMN IF NOT EXISTS quiet_hours_end TIME,
    ADD COLUMN IF NOT EXISTS send_window_start TIME,
    ADD COLUMN IF NOT EXISTS send_window_end TIME,
    ADD COLUMN IF NOT EXISTS frequency_cap INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS lifecycle_note TEXT,
    ADD COLUMN IF NOT EXISTS experiment_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS trigger_source VARCHAR(128),
    ADD COLUMN IF NOT EXISTS trigger_reference VARCHAR(128);

UPDATE campaigns
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE campaigns
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_campaigns_tenant_workspace
    ON campaigns (tenant_id, workspace_id)
    WHERE deleted_at IS NULL;

-- Campaign audiences ownership alignment
ALTER TABLE campaign_audiences
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE';

UPDATE campaign_audiences a
SET workspace_id = COALESCE(c.workspace_id, 'workspace-default')
FROM campaigns c
WHERE a.campaign_id = c.id
  AND (a.workspace_id IS NULL OR a.workspace_id = '');

ALTER TABLE campaign_audiences
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_campaign_aud_tenant_workspace_campaign
    ON campaign_audiences (tenant_id, workspace_id, campaign_id)
    WHERE deleted_at IS NULL;

-- Send jobs ownership + lifecycle metadata
ALTER TABLE send_jobs
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE',
    ADD COLUMN IF NOT EXISTS paused_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS trigger_source VARCHAR(128),
    ADD COLUMN IF NOT EXISTS trigger_reference VARCHAR(128),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

UPDATE send_jobs j
SET workspace_id = COALESCE(c.workspace_id, 'workspace-default')
FROM campaigns c
WHERE j.campaign_id = c.id
  AND (j.workspace_id IS NULL OR j.workspace_id = '');

ALTER TABLE send_jobs
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_send_jobs_tenant_workspace_status
    ON send_jobs (tenant_id, workspace_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_send_jobs_tenant_workspace_campaign
    ON send_jobs (tenant_id, workspace_id, campaign_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_send_jobs_idempotency
    ON send_jobs (tenant_id, workspace_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;

-- Send batches ownership + diagnostics
ALTER TABLE send_batches
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE',
    ADD COLUMN IF NOT EXISTS last_error TEXT;

UPDATE send_batches b
SET workspace_id = COALESCE(j.workspace_id, 'workspace-default')
FROM send_jobs j
WHERE b.job_id = j.id
  AND (b.workspace_id IS NULL OR b.workspace_id = '');

ALTER TABLE send_batches
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_send_batches_tenant_workspace_job
    ON send_batches (tenant_id, workspace_id, job_id)
    WHERE deleted_at IS NULL;

-- Idempotency ledger for campaign consumers
CREATE TABLE IF NOT EXISTS campaign_event_idempotency (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_id VARCHAR(128),
    idempotency_key VARCHAR(128),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_campaign_event_idempotency_event
    ON campaign_event_idempotency (tenant_id, workspace_id, event_type, event_id)
    WHERE event_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_campaign_event_idempotency_key
    ON campaign_event_idempotency (tenant_id, workspace_id, event_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_campaign_event_idempotency_processed
    ON campaign_event_idempotency (tenant_id, workspace_id, processed_at DESC);

-- Legacy ownership migration audit
CREATE TABLE IF NOT EXISTS campaign_workspace_migration_audit (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    old_workspace_id VARCHAR(64),
    new_workspace_id VARCHAR(64) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO campaign_workspace_migration_audit (id, tenant_id, resource_type, resource_id, old_workspace_id, new_workspace_id, reason, created_at)
SELECT
    substr(md5(id || ':workspace-default'), 1, 36),
    tenant_id,
    'CAMPAIGN',
    id,
    NULL,
    workspace_id,
    'LEGACY_BACKFILL',
    NOW()
FROM campaigns
WHERE workspace_id = 'workspace-default'
  AND NOT EXISTS (
      SELECT 1
      FROM campaign_workspace_migration_audit a
      WHERE a.resource_type = 'CAMPAIGN'
        AND a.resource_id = campaigns.id
  );
