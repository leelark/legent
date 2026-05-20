-- V16: Draft-only AI content assistance governance ledgers.

CREATE TABLE IF NOT EXISTS ai_content_assistance_policies (
    id                              VARCHAR(26) PRIMARY KEY,
    tenant_id                       VARCHAR(64) NOT NULL,
    workspace_id                    VARCHAR(64) NOT NULL,
    policy_key                      VARCHAR(128) NOT NULL,
    name                            VARCHAR(255) NOT NULL,
    status                          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    feature_class                   VARCHAR(64) NOT NULL DEFAULT 'DRAFT_CONTENT_ASSISTANCE',
    provider_disclosure             JSONB NOT NULL DEFAULT '{}',
    allowed_data_classes            JSONB NOT NULL DEFAULT '[]',
    prohibited_data_classes         JSONB NOT NULL DEFAULT '[]',
    training_usage_allowed          BOOLEAN NOT NULL DEFAULT FALSE,
    retention_policy                JSONB NOT NULL DEFAULT '{}',
    prompt_storage_policy           VARCHAR(32) NOT NULL DEFAULT 'HASH_ONLY',
    output_storage_policy           VARCHAR(32) NOT NULL DEFAULT 'HASH_ONLY',
    opt_in_required                 BOOLEAN NOT NULL DEFAULT TRUE,
    opt_out_enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    require_human_review            BOOLEAN NOT NULL DEFAULT TRUE,
    draft_only                      BOOLEAN NOT NULL DEFAULT TRUE,
    kill_switch_enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    version_label                   VARCHAR(64) NOT NULL DEFAULT 'v1',
    metadata                        JSONB NOT NULL DEFAULT '{}',
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                      VARCHAR(26),
    deleted_at                      TIMESTAMPTZ,
    version                         BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_content_assistance_policy_key
    ON ai_content_assistance_policies(tenant_id, workspace_id, policy_key)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_ai_content_assistance_policies_status
    ON ai_content_assistance_policies(tenant_id, workspace_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS ai_content_assistance_audits (
    id                              VARCHAR(26) PRIMARY KEY,
    tenant_id                       VARCHAR(64) NOT NULL,
    workspace_id                    VARCHAR(64) NOT NULL,
    policy_id                       VARCHAR(26) REFERENCES ai_content_assistance_policies(id) ON DELETE SET NULL,
    policy_key                      VARCHAR(128) NOT NULL,
    policy_version                  VARCHAR(64) NOT NULL,
    artifact_type                   VARCHAR(128),
    artifact_id                     VARCHAR(128),
    requested_action                VARCHAR(64) NOT NULL,
    decision                        VARCHAR(64) NOT NULL,
    prompt_template_version         VARCHAR(128),
    prompt_hash                     VARCHAR(64),
    output_hash                     VARCHAR(64),
    provider_disclosure             JSONB NOT NULL DEFAULT '{}',
    data_classes_used               JSONB NOT NULL DEFAULT '[]',
    data_classes_blocked            JSONB NOT NULL DEFAULT '[]',
    guardrail_findings              JSONB NOT NULL DEFAULT '[]',
    review_decision                 JSONB NOT NULL DEFAULT '{}',
    evidence_refs                   JSONB NOT NULL DEFAULT '[]',
    request_context                 JSONB NOT NULL DEFAULT '{}',
    human_reviewed                  BOOLEAN NOT NULL DEFAULT FALSE,
    provider_invoked                BOOLEAN NOT NULL DEFAULT FALSE,
    raw_prompt_stored               BOOLEAN NOT NULL DEFAULT FALSE,
    raw_output_stored               BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                      VARCHAR(26),
    deleted_at                      TIMESTAMPTZ,
    version                         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_ai_content_assistance_audits_scope
    ON ai_content_assistance_audits(tenant_id, workspace_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_ai_content_assistance_audits_artifact
    ON ai_content_assistance_audits(tenant_id, workspace_id, artifact_type, artifact_id, created_at DESC)
    WHERE deleted_at IS NULL;
