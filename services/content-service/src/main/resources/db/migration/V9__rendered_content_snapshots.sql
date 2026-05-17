-- V9: Durable tenant/workspace-scoped rendered content snapshots for delivery retries.

CREATE TABLE IF NOT EXISTS rendered_content_snapshots (
    id                         VARCHAR(36) PRIMARY KEY,
    tenant_id                  VARCHAR(36) NOT NULL,
    workspace_id               VARCHAR(64) NOT NULL,
    reference_id               VARCHAR(80) NOT NULL,
    campaign_id                VARCHAR(64) NOT NULL,
    job_id                     VARCHAR(64) NOT NULL,
    batch_id                   VARCHAR(64) NOT NULL,
    message_id                 VARCHAR(160) NOT NULL,
    content_id                 VARCHAR(64) NOT NULL,
    subject                    VARCHAR(500) NOT NULL,
    html_body                  TEXT NOT NULL,
    text_body                  TEXT,
    subject_sha256             VARCHAR(64) NOT NULL,
    html_sha256                VARCHAR(64) NOT NULL,
    text_sha256                VARCHAR(64),
    subject_bytes              INTEGER NOT NULL,
    html_bytes                 INTEGER NOT NULL,
    text_bytes                 INTEGER NOT NULL DEFAULT 0,
    inline_fallback_included   BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at                 TIMESTAMPTZ NOT NULL,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                 VARCHAR(36),
    deleted_at                 TIMESTAMPTZ,
    version                    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_rendered_content_snapshot_reference UNIQUE (tenant_id, workspace_id, reference_id)
);

CREATE INDEX IF NOT EXISTS idx_rendered_content_snapshot_scope_reference
    ON rendered_content_snapshots(tenant_id, workspace_id, reference_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_rendered_content_snapshot_expiry
    ON rendered_content_snapshots(expires_at)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_rendered_content_snapshot_campaign
    ON rendered_content_snapshots(tenant_id, workspace_id, campaign_id, job_id, batch_id)
    WHERE deleted_at IS NULL;
