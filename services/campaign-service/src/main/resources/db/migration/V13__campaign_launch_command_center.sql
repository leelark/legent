-- V13: Launch Command Center persistence and high-traffic campaign indexes.

CREATE TABLE IF NOT EXISTS campaign_launch_plans (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    campaign_id VARCHAR(26) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PREVIEWED',
    readiness_score INTEGER NOT NULL DEFAULT 0,
    blocker_count INTEGER NOT NULL DEFAULT 0,
    warning_count INTEGER NOT NULL DEFAULT 0,
    primary_action VARCHAR(64) NOT NULL DEFAULT 'RUN_READINESS',
    audit_id VARCHAR(64),
    executed_at TIMESTAMPTZ,
    request_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    summary_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_campaign_launch_plan_idempotency
    ON campaign_launch_plans (tenant_id, workspace_id, idempotency_key)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_campaign_launch_plan_campaign
    ON campaign_launch_plans (tenant_id, workspace_id, campaign_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS campaign_launch_steps (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    launch_plan_id VARCHAR(26) NOT NULL,
    campaign_id VARCHAR(26) NOT NULL,
    step_key VARCHAR(64) NOT NULL,
    step_label VARCHAR(160) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PASS',
    score INTEGER NOT NULL DEFAULT 0,
    message VARCHAR(500),
    sort_order INTEGER NOT NULL DEFAULT 0,
    details_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_campaign_launch_steps_plan
    ON campaign_launch_steps (tenant_id, workspace_id, launch_plan_id, sort_order)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_campaigns_scope_status_updated
    ON campaigns (tenant_id, workspace_id, status, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_send_jobs_scope_status_schedule
    ON send_jobs (tenant_id, workspace_id, status, scheduled_at, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_campaign_approvals_scope_status
    ON campaign_approvals (tenant_id, campaign_id, status, requested_at DESC)
    WHERE deleted_at IS NULL;
