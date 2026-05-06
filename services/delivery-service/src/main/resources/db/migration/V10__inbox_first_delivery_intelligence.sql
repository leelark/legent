-- Inbox-first delivery intelligence.

ALTER TABLE message_logs
    ADD COLUMN IF NOT EXISTS safety_decision VARCHAR(32),
    ADD COLUMN IF NOT EXISTS risk_score INT,
    ADD COLUMN IF NOT EXISTS provider_score INT,
    ADD COLUMN IF NOT EXISTS rate_limit_key VARCHAR(500),
    ADD COLUMN IF NOT EXISTS warmup_stage VARCHAR(64),
    ADD COLUMN IF NOT EXISTS suppression_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_message_logs_safety
    ON message_logs(tenant_id, workspace_id, safety_decision, risk_score);

CREATE TABLE IF NOT EXISTS delivery_safety_evaluations (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    campaign_id VARCHAR(64),
    job_id VARCHAR(64),
    batch_id VARCHAR(64),
    message_id VARCHAR(255),
    subscriber_id VARCHAR(64),
    email VARCHAR(320),
    sender_domain VARCHAR(255),
    recipient_domain VARCHAR(255),
    provider_id VARCHAR(64),
    decision VARCHAR(32) NOT NULL,
    risk_score INT NOT NULL,
    max_rate_per_minute INT,
    allowed_audience_count INT,
    reason_codes TEXT,
    remediation_hints TEXT,
    rate_limit_key VARCHAR(500),
    warmup_stage VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(26),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_delivery_safety_tenant_workspace
    ON delivery_safety_evaluations(tenant_id, workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_delivery_safety_campaign
    ON delivery_safety_evaluations(tenant_id, workspace_id, campaign_id);

CREATE TABLE IF NOT EXISTS delivery_send_rate_state (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    rate_limit_key VARCHAR(500) NOT NULL,
    sender_domain VARCHAR(255),
    provider_id VARCHAR(64),
    isp_domain VARCHAR(255),
    max_per_minute INT NOT NULL,
    used_this_minute INT NOT NULL DEFAULT 0,
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    throttle_state VARCHAR(32) NOT NULL,
    risk_score INT NOT NULL DEFAULT 0,
    last_adjusted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(26),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_delivery_send_rate_key
    ON delivery_send_rate_state(tenant_id, workspace_id, rate_limit_key)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_delivery_send_rate_workspace
    ON delivery_send_rate_state(tenant_id, workspace_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS delivery_warmup_state (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    sender_domain VARCHAR(255) NOT NULL,
    provider_id VARCHAR(64) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    hourly_limit INT NOT NULL,
    daily_limit INT NOT NULL,
    sent_this_hour INT NOT NULL DEFAULT 0,
    sent_today INT NOT NULL DEFAULT 0,
    hour_window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    day_window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    bounce_rate DOUBLE PRECISION DEFAULT 0,
    complaint_rate DOUBLE PRECISION DEFAULT 0,
    rollback_reason TEXT,
    next_increase_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(26),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_delivery_warmup_scope
    ON delivery_warmup_state(tenant_id, workspace_id, sender_domain, provider_id)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_delivery_warmup_workspace
    ON delivery_warmup_state(tenant_id, workspace_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS provider_decision_traces (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    provider_id VARCHAR(64) NOT NULL,
    sender_domain VARCHAR(255),
    recipient_domain VARCHAR(255),
    score INT NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT false,
    factors TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(26),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_provider_decision_trace_workspace
    ON provider_decision_traces(tenant_id, workspace_id, created_at DESC);
