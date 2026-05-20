-- V8: Scoped automation artifact metadata for file, import, and extract activity families.

CREATE TABLE IF NOT EXISTS automation_artifacts (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(26) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    activity_id VARCHAR(26),
    source_kind VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'READY',
    object_key VARCHAR(512) NOT NULL,
    display_name VARCHAR(255),
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    retention_policy VARCHAR(64) NOT NULL DEFAULT 'AUTOMATION_30_DAYS',
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_automation_artifact_object UNIQUE (tenant_id, workspace_id, object_key)
);

CREATE INDEX IF NOT EXISTS idx_automation_artifacts_scope_status
    ON automation_artifacts (tenant_id, workspace_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_automation_artifacts_activity
    ON automation_artifacts (tenant_id, workspace_id, activity_id)
    WHERE deleted_at IS NULL;

ALTER TABLE automation_activity_runs
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_automation_activity_runs_idempotency
    ON automation_activity_runs (tenant_id, workspace_id, activity_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
