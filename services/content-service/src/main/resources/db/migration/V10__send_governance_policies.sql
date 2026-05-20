-- V10: Tenant/workspace-scoped Email Studio send governance policy catalog.

CREATE TABLE IF NOT EXISTS send_governance_policies (
    id                          VARCHAR(26) PRIMARY KEY,
    tenant_id                   VARCHAR(36) NOT NULL,
    workspace_id                VARCHAR(64) NOT NULL,
    policy_key                  VARCHAR(128) NOT NULL,
    name                        VARCHAR(255) NOT NULL,
    description                 TEXT,
    classification              VARCHAR(32) NOT NULL DEFAULT 'COMMERCIAL',
    sender_profile_id           VARCHAR(64),
    delivery_profile_id         VARCHAR(64),
    sending_domain              VARCHAR(255),
    provider_id                 VARCHAR(64),
    unsubscribe_policy          VARCHAR(32) NOT NULL DEFAULT 'REQUIRED',
    suppression_required        BOOLEAN NOT NULL DEFAULT TRUE,
    consent_required            BOOLEAN NOT NULL DEFAULT FALSE,
    tracking_allowed            BOOLEAN NOT NULL DEFAULT TRUE,
    send_log_retention_days     INTEGER NOT NULL DEFAULT 365,
    publication_policy          VARCHAR(64) NOT NULL DEFAULT 'APPROVED_CONTENT_REQUIRED',
    active                      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(26),
    deleted_at                  TIMESTAMPTZ,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_send_governance_classification
        CHECK (classification IN ('COMMERCIAL', 'TRANSACTIONAL', 'OPERATIONAL')),
    CONSTRAINT ck_send_governance_unsubscribe_policy
        CHECK (unsubscribe_policy IN ('REQUIRED', 'OPTIONAL', 'NOT_APPLICABLE')),
    CONSTRAINT ck_send_governance_retention_days
        CHECK (send_log_retention_days BETWEEN 1 AND 2555),
    CONSTRAINT ck_send_governance_commercial_controls
        CHECK (classification <> 'COMMERCIAL' OR (suppression_required = TRUE AND unsubscribe_policy = 'REQUIRED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_send_governance_policy_workspace_key
    ON send_governance_policies (tenant_id, workspace_id, lower(policy_key))
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_send_governance_policy_scope
    ON send_governance_policies (tenant_id, workspace_id, active, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_send_governance_policy_sender_domain
    ON send_governance_policies (tenant_id, workspace_id, sending_domain)
    WHERE deleted_at IS NULL AND sending_domain IS NOT NULL;
