-- V18: AI provider contract, disclosure, metering, and kill-switch ledger.

CREATE TABLE IF NOT EXISTS ai_provider_contracts (
    id                              VARCHAR(26) PRIMARY KEY,
    tenant_id                       VARCHAR(64) NOT NULL,
    workspace_id                    VARCHAR(64) NOT NULL,
    contract_key                    VARCHAR(128) NOT NULL,
    provider_key                    VARCHAR(128) NOT NULL,
    provider_name                   VARCHAR(255) NOT NULL,
    model_name                      VARCHAR(255) NOT NULL,
    status                          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    feature_class                   VARCHAR(64) NOT NULL DEFAULT 'AI_PROVIDER_ACCESS',
    provider_disclosure             JSONB NOT NULL DEFAULT '{}',
    allowed_data_classes            JSONB NOT NULL DEFAULT '[]',
    prohibited_data_classes         JSONB NOT NULL DEFAULT '[]',
    training_usage_allowed          BOOLEAN NOT NULL DEFAULT FALSE,
    opt_in_required                 BOOLEAN NOT NULL DEFAULT TRUE,
    opt_out_enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    require_human_review            BOOLEAN NOT NULL DEFAULT TRUE,
    metering_enabled                BOOLEAN NOT NULL DEFAULT TRUE,
    kill_switch_enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    prompt_storage_policy           VARCHAR(32) NOT NULL DEFAULT 'HASH_ONLY',
    output_storage_policy           VARCHAR(32) NOT NULL DEFAULT 'HASH_ONLY',
    max_units_per_request           NUMERIC(18, 6) NOT NULL DEFAULT 0,
    monthly_unit_limit              NUMERIC(18, 6) NOT NULL DEFAULT 0,
    cost_policy                     JSONB NOT NULL DEFAULT '{}',
    retention_policy                JSONB NOT NULL DEFAULT '{}',
    version_label                   VARCHAR(64) NOT NULL DEFAULT 'v1',
    metadata                        JSONB NOT NULL DEFAULT '{}',
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                      VARCHAR(26),
    deleted_at                      TIMESTAMPTZ,
    version                         BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_provider_contract_key
    ON ai_provider_contracts(tenant_id, workspace_id, contract_key)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ai_provider_contracts_status
    ON ai_provider_contracts(tenant_id, workspace_id, status, provider_key, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS ai_provider_metering_events (
    id                              VARCHAR(26) PRIMARY KEY,
    tenant_id                       VARCHAR(64) NOT NULL,
    workspace_id                    VARCHAR(64) NOT NULL,
    contract_id                     VARCHAR(26) REFERENCES ai_provider_contracts(id) ON DELETE SET NULL,
    contract_key                    VARCHAR(128) NOT NULL,
    provider_key                    VARCHAR(128) NOT NULL,
    provider_name                   VARCHAR(255) NOT NULL,
    model_name                      VARCHAR(255) NOT NULL,
    feature_key                     VARCHAR(128),
    artifact_type                   VARCHAR(128),
    artifact_id                     VARCHAR(128),
    request_id                      VARCHAR(128) NOT NULL,
    requested_action                VARCHAR(64) NOT NULL,
    decision                        VARCHAR(64) NOT NULL,
    units_requested                 NUMERIC(18, 6) NOT NULL DEFAULT 0,
    cost_estimate                   NUMERIC(18, 6) NOT NULL DEFAULT 0,
    currency_code                   VARCHAR(12) NOT NULL DEFAULT 'USD',
    data_classes_requested          JSONB NOT NULL DEFAULT '[]',
    data_classes_blocked            JSONB NOT NULL DEFAULT '[]',
    provider_disclosure             JSONB NOT NULL DEFAULT '{}',
    policy_snapshot                 JSONB NOT NULL DEFAULT '{}',
    guardrail_findings              JSONB NOT NULL DEFAULT '[]',
    evidence_refs                   JSONB NOT NULL DEFAULT '[]',
    request_context                 JSONB NOT NULL DEFAULT '{}',
    provider_invoked                BOOLEAN NOT NULL DEFAULT FALSE,
    raw_prompt_stored               BOOLEAN NOT NULL DEFAULT FALSE,
    raw_output_stored               BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                      VARCHAR(26),
    deleted_at                      TIMESTAMPTZ,
    version                         BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_provider_metering_request
    ON ai_provider_metering_events(tenant_id, workspace_id, request_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ai_provider_metering_events_scope
    ON ai_provider_metering_events(tenant_id, workspace_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_ai_provider_metering_events_contract_decision
    ON ai_provider_metering_events(tenant_id, workspace_id, contract_key, decision, created_at DESC)
    WHERE deleted_at IS NULL;
