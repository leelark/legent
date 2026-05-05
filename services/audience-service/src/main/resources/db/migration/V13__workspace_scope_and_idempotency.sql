-- V13: Workspace-scoped ownership, audience idempotency, and merge audit structures

-- Subscribers
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS team_id VARCHAR(36);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS assigned_owner_id VARCHAR(36);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(30) NOT NULL DEFAULT 'WORKSPACE';
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(1024);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS date_of_birth DATE;
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS gender VARCHAR(32);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS company VARCHAR(255);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS job_title VARCHAR(255);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS industry VARCHAR(255);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS department VARCHAR(255);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS country VARCHAR(128);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS state VARCHAR(128);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS city VARCHAR(128);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS language VARCHAR(64);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS lead_source VARCHAR(128);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS acquisition_channel VARCHAR(128);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS campaign_source VARCHAR(128);
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS categories JSONB NOT NULL DEFAULT '[]';
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS tags JSONB NOT NULL DEFAULT '[]';
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS internal_notes TEXT;
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS lifecycle_stage VARCHAR(64) NOT NULL DEFAULT 'PROSPECT';
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS lifecycle_stage_at TIMESTAMPTZ;
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS open_score INT NOT NULL DEFAULT 0;
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS click_score INT NOT NULL DEFAULT 0;
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS conversion_score INT NOT NULL DEFAULT 0;
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS recency_score INT NOT NULL DEFAULT 0;
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS frequency_score INT NOT NULL DEFAULT 0;
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS engagement_score INT NOT NULL DEFAULT 0;
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS activity_score INT NOT NULL DEFAULT 0;
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS total_score INT NOT NULL DEFAULT 0;
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS timeline JSONB NOT NULL DEFAULT '[]';

UPDATE subscribers
SET workspace_id = COALESCE(workspace_id, 'workspace-default')
WHERE workspace_id IS NULL;
ALTER TABLE subscribers ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE subscribers DROP CONSTRAINT IF EXISTS uq_subscriber_tenant_email;
ALTER TABLE subscribers DROP CONSTRAINT IF EXISTS uk_subscriber_tenant_email;
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriber_tenant_workspace_email_active
    ON subscribers (tenant_id, workspace_id, lower(email))
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_sub_tenant_workspace_created
    ON subscribers (tenant_id, workspace_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- Lists
ALTER TABLE subscriber_lists ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE subscriber_lists ADD COLUMN IF NOT EXISTS team_id VARCHAR(36);
ALTER TABLE subscriber_lists ADD COLUMN IF NOT EXISTS assigned_owner_id VARCHAR(36);
ALTER TABLE subscriber_lists ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(30) NOT NULL DEFAULT 'WORKSPACE';
ALTER TABLE subscriber_lists ADD COLUMN IF NOT EXISTS is_dynamic BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE subscriber_lists ADD COLUMN IF NOT EXISTS auto_refresh_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE subscriber_lists ADD COLUMN IF NOT EXISTS visibility_scope VARCHAR(30) NOT NULL DEFAULT 'WORKSPACE';
ALTER TABLE subscriber_lists ADD COLUMN IF NOT EXISTS is_favorite BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE subscriber_lists ADD COLUMN IF NOT EXISTS folder VARCHAR(255);
ALTER TABLE subscriber_lists ADD COLUMN IF NOT EXISTS tags JSONB NOT NULL DEFAULT '[]';
ALTER TABLE subscriber_lists ADD COLUMN IF NOT EXISTS category VARCHAR(128);

UPDATE subscriber_lists
SET workspace_id = COALESCE(workspace_id, 'workspace-default')
WHERE workspace_id IS NULL;
ALTER TABLE subscriber_lists ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE subscriber_lists DROP CONSTRAINT IF EXISTS uq_list_tenant_name;
CREATE UNIQUE INDEX IF NOT EXISTS uq_list_tenant_workspace_name_active
    ON subscriber_lists (tenant_id, workspace_id, lower(name))
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_list_tenant_workspace
    ON subscriber_lists (tenant_id, workspace_id)
    WHERE deleted_at IS NULL;

-- Segments
ALTER TABLE segments ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE segments ADD COLUMN IF NOT EXISTS team_id VARCHAR(36);
ALTER TABLE segments ADD COLUMN IF NOT EXISTS assigned_owner_id VARCHAR(36);
ALTER TABLE segments ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(30) NOT NULL DEFAULT 'WORKSPACE';
ALTER TABLE segments ADD COLUMN IF NOT EXISTS folder VARCHAR(255);
ALTER TABLE segments ADD COLUMN IF NOT EXISTS tags JSONB NOT NULL DEFAULT '[]';
ALTER TABLE segments ADD COLUMN IF NOT EXISTS category VARCHAR(128);
ALTER TABLE segments ADD COLUMN IF NOT EXISTS is_favorite BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE segments ADD COLUMN IF NOT EXISTS saved_filter JSONB;

UPDATE segments
SET workspace_id = COALESCE(workspace_id, 'workspace-default')
WHERE workspace_id IS NULL;
ALTER TABLE segments ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE segments DROP CONSTRAINT IF EXISTS uq_seg_tenant_name;
CREATE UNIQUE INDEX IF NOT EXISTS uq_segment_tenant_workspace_name_active
    ON segments (tenant_id, workspace_id, lower(name))
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_segment_tenant_workspace
    ON segments (tenant_id, workspace_id)
    WHERE deleted_at IS NULL;

-- Suppressions
ALTER TABLE suppressions ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE suppressions ADD COLUMN IF NOT EXISTS team_id VARCHAR(36);
ALTER TABLE suppressions ADD COLUMN IF NOT EXISTS assigned_owner_id VARCHAR(36);
ALTER TABLE suppressions ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(30) NOT NULL DEFAULT 'WORKSPACE';
ALTER TABLE suppressions ADD COLUMN IF NOT EXISTS suppression_scope VARCHAR(30) NOT NULL DEFAULT 'WORKSPACE';
ALTER TABLE suppressions ADD COLUMN IF NOT EXISTS source_ref VARCHAR(255);
ALTER TABLE suppressions ADD COLUMN IF NOT EXISTS recovery_status VARCHAR(30) NOT NULL DEFAULT 'NONE';
ALTER TABLE suppressions ADD COLUMN IF NOT EXISTS recovered_at TIMESTAMPTZ;

UPDATE suppressions
SET workspace_id = COALESCE(workspace_id, 'workspace-default')
WHERE workspace_id IS NULL;
ALTER TABLE suppressions ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE suppressions DROP CONSTRAINT IF EXISTS uq_suppression;
CREATE UNIQUE INDEX IF NOT EXISTS uq_suppression_tenant_workspace_email_type_active
    ON suppressions (tenant_id, workspace_id, lower(email), suppression_type)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_suppression_tenant_workspace
    ON suppressions (tenant_id, workspace_id)
    WHERE deleted_at IS NULL;

-- Import jobs
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS team_id VARCHAR(36);
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS assigned_owner_id VARCHAR(36);
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(30) NOT NULL DEFAULT 'WORKSPACE';
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS import_type VARCHAR(30) NOT NULL DEFAULT 'CSV';
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS resume_token VARCHAR(128);
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS started_by VARCHAR(36);

UPDATE import_jobs
SET workspace_id = COALESCE(workspace_id, 'workspace-default')
WHERE workspace_id IS NULL;
ALTER TABLE import_jobs ALTER COLUMN workspace_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_import_tenant_workspace_status
    ON import_jobs (tenant_id, workspace_id, status);

-- Membership tables
ALTER TABLE list_memberships ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE list_memberships ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(30) NOT NULL DEFAULT 'WORKSPACE';
UPDATE list_memberships
SET workspace_id = COALESCE(workspace_id, 'workspace-default')
WHERE workspace_id IS NULL;
ALTER TABLE list_memberships ALTER COLUMN workspace_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_list_membership_tenant_workspace_list
    ON list_memberships (tenant_id, workspace_id, list_id);

ALTER TABLE segment_memberships ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE segment_memberships ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(30) NOT NULL DEFAULT 'WORKSPACE';
UPDATE segment_memberships
SET workspace_id = COALESCE(workspace_id, 'workspace-default')
WHERE workspace_id IS NULL;
ALTER TABLE segment_memberships ALTER COLUMN workspace_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_segment_membership_tenant_workspace_segment
    ON segment_memberships (tenant_id, workspace_id, segment_id);

-- Consent / double opt-in
ALTER TABLE consent_records ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE consent_records ADD COLUMN IF NOT EXISTS team_id VARCHAR(36);
ALTER TABLE consent_records ADD COLUMN IF NOT EXISTS assigned_owner_id VARCHAR(36);
ALTER TABLE consent_records ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(30) NOT NULL DEFAULT 'WORKSPACE';
UPDATE consent_records
SET workspace_id = COALESCE(workspace_id, 'workspace-default')
WHERE workspace_id IS NULL;
ALTER TABLE consent_records ALTER COLUMN workspace_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_consent_tenant_workspace_subscriber
    ON consent_records (tenant_id, workspace_id, subscriber_id);

ALTER TABLE double_optin_tokens ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE double_optin_tokens ADD COLUMN IF NOT EXISTS team_id VARCHAR(36);
ALTER TABLE double_optin_tokens ADD COLUMN IF NOT EXISTS assigned_owner_id VARCHAR(36);
ALTER TABLE double_optin_tokens ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(30) NOT NULL DEFAULT 'WORKSPACE';
UPDATE double_optin_tokens
SET workspace_id = COALESCE(workspace_id, 'workspace-default')
WHERE workspace_id IS NULL;
ALTER TABLE double_optin_tokens ALTER COLUMN workspace_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_double_optin_tenant_workspace_subscriber
    ON double_optin_tokens (tenant_id, workspace_id, subscriber_id);

-- Audience event idempotency
CREATE TABLE IF NOT EXISTS audience_event_idempotency (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    workspace_id        VARCHAR(36) NOT NULL,
    event_type          VARCHAR(120) NOT NULL,
    event_id            VARCHAR(120),
    idempotency_key     VARCHAR(120),
    processed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(36),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_audience_idem_event
    ON audience_event_idempotency (tenant_id, workspace_id, event_type, event_id)
    WHERE event_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_audience_idem_key
    ON audience_event_idempotency (tenant_id, workspace_id, event_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Subscriber dedupe/merge audit
CREATE TABLE IF NOT EXISTS subscriber_merge_log (
    id                      VARCHAR(36) PRIMARY KEY,
    tenant_id               VARCHAR(36) NOT NULL,
    workspace_id            VARCHAR(36) NOT NULL,
    winner_subscriber_id    VARCHAR(36) NOT NULL,
    merged_subscriber_id    VARCHAR(36) NOT NULL,
    merge_reason            VARCHAR(255),
    merged_fields           JSONB NOT NULL DEFAULT '{}',
    metadata                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(36),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_subscriber_merge_log_scope
    ON subscriber_merge_log (tenant_id, workspace_id, created_at DESC);

CREATE TABLE IF NOT EXISTS subscriber_merge_conflicts (
    id                      VARCHAR(36) PRIMARY KEY,
    tenant_id               VARCHAR(36) NOT NULL,
    workspace_id            VARCHAR(36) NOT NULL,
    winner_subscriber_id    VARCHAR(36) NOT NULL,
    loser_subscriber_id     VARCHAR(36) NOT NULL,
    conflict_field          VARCHAR(128) NOT NULL,
    winner_value            JSONB,
    loser_value             JSONB,
    resolution              VARCHAR(64) NOT NULL DEFAULT 'WINNER_PRESERVED',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(36),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_subscriber_merge_conflicts_scope
    ON subscriber_merge_conflicts (tenant_id, workspace_id, created_at DESC);

CREATE TABLE IF NOT EXISTS subscriber_identity_provenance (
    id                      VARCHAR(36) PRIMARY KEY,
    tenant_id               VARCHAR(36) NOT NULL,
    workspace_id            VARCHAR(36) NOT NULL,
    subscriber_id           VARCHAR(36) NOT NULL,
    source_type             VARCHAR(64) NOT NULL,
    source_ref              VARCHAR(255),
    captured_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(36),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_subscriber_identity_provenance_scope
    ON subscriber_identity_provenance (tenant_id, workspace_id, subscriber_id, captured_at DESC);
