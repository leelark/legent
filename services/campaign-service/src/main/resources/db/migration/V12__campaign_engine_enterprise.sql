-- V12: First-class campaign engine experiments, locks, budgets, ledgers, and DLQ.

CREATE TABLE IF NOT EXISTS campaign_experiments (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    campaign_id VARCHAR(26) NOT NULL,
    name VARCHAR(255) NOT NULL,
    experiment_type VARCHAR(32) NOT NULL DEFAULT 'AB',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    winner_metric VARCHAR(32) NOT NULL DEFAULT 'CLICKS',
    custom_metric_name VARCHAR(128),
    auto_promotion BOOLEAN NOT NULL DEFAULT FALSE,
    min_recipients_per_variant INTEGER NOT NULL DEFAULT 100,
    evaluation_window_hours INTEGER NOT NULL DEFAULT 24,
    holdout_percentage NUMERIC(6,2) NOT NULL DEFAULT 0,
    winner_variant_id VARCHAR(26),
    factors JSONB NOT NULL DEFAULT '[]'::jsonb,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_campaign_experiments_scope
    ON campaign_experiments (tenant_id, workspace_id, campaign_id, status)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_campaign_experiments_one_active
    ON campaign_experiments (tenant_id, workspace_id, campaign_id)
    WHERE deleted_at IS NULL AND status IN ('ACTIVE', 'PROMOTED');

CREATE TABLE IF NOT EXISTS campaign_variants (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    campaign_id VARCHAR(26) NOT NULL,
    experiment_id VARCHAR(26) NOT NULL,
    variant_key VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    weight INTEGER NOT NULL DEFAULT 50,
    control_variant BOOLEAN NOT NULL DEFAULT FALSE,
    holdout_variant BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    winner BOOLEAN NOT NULL DEFAULT FALSE,
    content_id VARCHAR(64),
    subject_override VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_campaign_variants_experiment
    ON campaign_variants (tenant_id, workspace_id, experiment_id, active)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_campaign_variant_key
    ON campaign_variants (tenant_id, workspace_id, experiment_id, lower(variant_key))
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS campaign_frequency_policies (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    campaign_id VARCHAR(26) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    max_sends INTEGER NOT NULL DEFAULT 0,
    window_hours INTEGER NOT NULL DEFAULT 24,
    include_journeys BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_campaign_frequency_policy
    ON campaign_frequency_policies (tenant_id, workspace_id, campaign_id)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS campaign_budgets (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    campaign_id VARCHAR(26) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    budget_limit NUMERIC(18,6) NOT NULL DEFAULT 0,
    cost_per_send NUMERIC(18,6) NOT NULL DEFAULT 0,
    reserved_spend NUMERIC(18,6) NOT NULL DEFAULT 0,
    actual_spend NUMERIC(18,6) NOT NULL DEFAULT 0,
    enforced BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_campaign_budget
    ON campaign_budgets (tenant_id, workspace_id, campaign_id)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS campaign_send_ledger (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    campaign_id VARCHAR(26) NOT NULL,
    job_id VARCHAR(64),
    batch_id VARCHAR(64),
    message_id VARCHAR(256) NOT NULL,
    subscriber_id VARCHAR(64),
    email VARCHAR(320) NOT NULL,
    experiment_id VARCHAR(26),
    variant_id VARCHAR(26),
    send_state VARCHAR(32) NOT NULL,
    reason VARCHAR(128),
    cost_reserved NUMERIC(18,6) NOT NULL DEFAULT 0,
    cost_actual NUMERIC(18,6) NOT NULL DEFAULT 0,
    sent_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_campaign_send_ledger_message
    ON campaign_send_ledger (tenant_id, workspace_id, message_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_campaign_send_ledger_recipient_window
    ON campaign_send_ledger (tenant_id, workspace_id, lower(email), created_at DESC, send_state)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_campaign_send_ledger_variant
    ON campaign_send_ledger (tenant_id, workspace_id, campaign_id, experiment_id, variant_id, send_state)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS campaign_dead_letters (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    campaign_id VARCHAR(26) NOT NULL,
    job_id VARCHAR(64) NOT NULL,
    batch_id VARCHAR(64),
    subscriber_id VARCHAR(64),
    email VARCHAR(320),
    reason VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    retry_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    next_retry_at TIMESTAMPTZ,
    replayed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_campaign_dead_letters_job
    ON campaign_dead_letters (tenant_id, workspace_id, job_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS campaign_locks (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    campaign_id VARCHAR(26) NOT NULL,
    lock_hash VARCHAR(128) NOT NULL,
    snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    locked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    locked_by VARCHAR(64),
    superseded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_campaign_lock_active
    ON campaign_locks (tenant_id, workspace_id, campaign_id)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS campaign_variant_metrics (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    campaign_id VARCHAR(26) NOT NULL,
    experiment_id VARCHAR(26) NOT NULL,
    variant_id VARCHAR(26),
    target_count BIGINT NOT NULL DEFAULT 0,
    holdout_count BIGINT NOT NULL DEFAULT 0,
    sent_count BIGINT NOT NULL DEFAULT 0,
    failed_count BIGINT NOT NULL DEFAULT 0,
    open_count BIGINT NOT NULL DEFAULT 0,
    click_count BIGINT NOT NULL DEFAULT 0,
    conversion_count BIGINT NOT NULL DEFAULT 0,
    revenue NUMERIC(18,6) NOT NULL DEFAULT 0,
    custom_metric_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_campaign_variant_metrics
    ON campaign_variant_metrics (tenant_id, workspace_id, campaign_id, experiment_id, COALESCE(variant_id, 'HOLDOUT'))
    WHERE deleted_at IS NULL;
