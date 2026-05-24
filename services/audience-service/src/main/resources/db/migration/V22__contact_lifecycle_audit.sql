-- V22: Append-only contact lifecycle audit for retention, deletion, preference, suppression, and explicit eligibility checks.
CREATE TABLE IF NOT EXISTS contact_lifecycle_audit (
    id                 VARCHAR(26) PRIMARY KEY,
    tenant_id          VARCHAR(36) NOT NULL,
    workspace_id       VARCHAR(36) NOT NULL,
    subject_type       VARCHAR(40) NOT NULL,
    subject_id         VARCHAR(36),
    subscriber_id      VARCHAR(36),
    data_extension_id  VARCHAR(36),
    email_sha256       CHAR(64),
    action             VARCHAR(80) NOT NULL,
    outcome            VARCHAR(40) NOT NULL DEFAULT 'SUCCEEDED',
    source             VARCHAR(80) NOT NULL DEFAULT 'AUDIENCE_SERVICE',
    source_event_id    VARCHAR(120),
    idempotency_key    VARCHAR(120),
    operation_id       VARCHAR(120),
    performed_by       VARCHAR(36),
    occurred_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata           JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(26),
    deleted_at         TIMESTAMPTZ,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_contact_lifecycle_subject_type
        CHECK (subject_type IN ('SUBSCRIBER', 'SUPPRESSION', 'PREFERENCE', 'DATA_EXTENSION', 'SEND_ELIGIBILITY')),
    CONSTRAINT chk_contact_lifecycle_outcome
        CHECK (outcome IN ('SUCCEEDED', 'DENIED', 'SKIPPED', 'FAILED')),
    CONSTRAINT chk_contact_lifecycle_email_hash
        CHECK (email_sha256 IS NULL OR email_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX IF NOT EXISTS idx_contact_lifecycle_audit_scope
    ON contact_lifecycle_audit (tenant_id, workspace_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_contact_lifecycle_audit_subject
    ON contact_lifecycle_audit (tenant_id, workspace_id, subject_type, subject_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_contact_lifecycle_audit_subscriber
    ON contact_lifecycle_audit (tenant_id, workspace_id, subscriber_id, occurred_at DESC)
    WHERE subscriber_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_contact_lifecycle_audit_data_extension
    ON contact_lifecycle_audit (tenant_id, workspace_id, data_extension_id, occurred_at DESC)
    WHERE data_extension_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_contact_lifecycle_audit_action
    ON contact_lifecycle_audit (tenant_id, workspace_id, action, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_contact_lifecycle_audit_email_hash
    ON contact_lifecycle_audit (tenant_id, workspace_id, email_sha256, occurred_at DESC)
    WHERE email_sha256 IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_contact_lifecycle_audit_occurred_brin
    ON contact_lifecycle_audit USING BRIN (occurred_at);

CREATE UNIQUE INDEX IF NOT EXISTS uq_contact_lifecycle_audit_source_event
    ON contact_lifecycle_audit (tenant_id, workspace_id, subject_type, subject_id, source, source_event_id)
    WHERE source_event_id IS NOT NULL;
