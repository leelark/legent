-- V7: Deliverability workspace strict ownership and consumer idempotency.

-- Sender domains become workspace-aware (still tenant-root compatible)
ALTER TABLE sender_domains
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE';

UPDATE sender_domains
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE sender_domains
    ALTER COLUMN workspace_id SET NOT NULL;

DROP INDEX IF EXISTS uq_sender_domains_tenant;
CREATE UNIQUE INDEX IF NOT EXISTS uq_sender_domains_tenant_workspace_domain
    ON sender_domains (tenant_id, workspace_id, domain_name)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_sender_domains_workspace
    ON sender_domains (tenant_id, workspace_id, status, updated_at DESC);

-- Domain reputation ownership
ALTER TABLE domain_reputations
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE';

UPDATE domain_reputations
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE domain_reputations
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_domain_reputation_workspace
    ON domain_reputations (tenant_id, workspace_id, domain_id, calculated_at DESC);

-- Suppression list ownership + scope
ALTER TABLE suppression_list
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE',
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

UPDATE suppression_list
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE suppression_list
    ALTER COLUMN workspace_id SET NOT NULL;

DROP INDEX IF EXISTS uq_suppressions_tenant_email;
CREATE UNIQUE INDEX IF NOT EXISTS uq_suppressions_tenant_workspace_email
    ON suppression_list (tenant_id, workspace_id, email, ownership_scope);

CREATE INDEX IF NOT EXISTS idx_suppressions_workspace_reason
    ON suppression_list (tenant_id, workspace_id, reason, created_at DESC);

-- DMARC reports ownership scope
ALTER TABLE dmarc_reports
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE';

UPDATE dmarc_reports
SET workspace_id = COALESCE(workspace_id, 'workspace-default')
WHERE workspace_id IS NULL OR workspace_id = '';

CREATE INDEX IF NOT EXISTS idx_dmarc_reports_scope
    ON dmarc_reports (tenant_id, workspace_id, domain, received_at DESC);

-- Legacy reputation score table compatibility: workspace tag for transition reads
ALTER TABLE reputation_scores
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source VARCHAR(32) NOT NULL DEFAULT 'LEGACY';

UPDATE reputation_scores
SET workspace_id = COALESCE(workspace_id, 'workspace-default'),
    source = COALESCE(source, 'LEGACY')
WHERE workspace_id IS NULL OR workspace_id = '' OR source IS NULL OR source = '';

CREATE INDEX IF NOT EXISTS idx_reputation_scores_scope
    ON reputation_scores (tenant_id, workspace_id, domain);

-- Idempotency ledger for deliverability Kafka consumers
CREATE TABLE IF NOT EXISTS deliverability_event_idempotency (
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

CREATE UNIQUE INDEX IF NOT EXISTS uq_deliverability_idempotency_event
    ON deliverability_event_idempotency (tenant_id, workspace_id, event_type, event_id)
    WHERE event_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_deliverability_idempotency_key
    ON deliverability_event_idempotency (tenant_id, workspace_id, event_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_deliverability_idempotency_processed
    ON deliverability_event_idempotency (tenant_id, workspace_id, processed_at DESC);
