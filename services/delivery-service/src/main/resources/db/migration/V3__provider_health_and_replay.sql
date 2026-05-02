-- =============================================
-- Delivery Service Provider Health and Replay Schema
-- Version: V3
-- Adds provider health monitoring and delivery replay
-- =============================================

-- ── Provider Health Monitoring ──
CREATE TABLE IF NOT EXISTS provider_health_checks (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    provider_id         VARCHAR(36) NOT NULL REFERENCES smtp_providers(id) ON DELETE CASCADE,
    check_timestamp     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status              VARCHAR(20) NOT NULL,
    response_time_ms    INT,
    error_message       TEXT,
    consecutive_failures INT DEFAULT 0,
    success_rate_24h    DECIMAL(5,2),
    total_sent_24h      BIGINT DEFAULT 0,
    total_failed_24h    BIGINT DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_provider_health_provider ON provider_health_checks(provider_id, check_timestamp DESC);
CREATE INDEX idx_provider_health_tenant ON provider_health_checks(tenant_id, check_timestamp DESC);
CREATE INDEX idx_provider_health_status ON provider_health_checks(status) WHERE status != 'HEALTHY';

-- ── Provider Scoring ──
CREATE TABLE IF NOT EXISTS provider_scores (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    provider_id         VARCHAR(36) NOT NULL REFERENCES smtp_providers(id) ON DELETE CASCADE,
    overall_score       INT NOT NULL DEFAULT 100,
    reliability_score   INT NOT NULL DEFAULT 100,
    speed_score         INT NOT NULL DEFAULT 100,
    reputation_score    INT NOT NULL DEFAULT 100,
    calculated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    score_factors       JSONB DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_provider_score UNIQUE (tenant_id, provider_id, calculated_at)
);

CREATE INDEX idx_provider_scores_provider ON provider_scores(provider_id, calculated_at DESC);
CREATE INDEX idx_provider_scores_tenant ON provider_scores(tenant_id, overall_score DESC);

-- ── Delivery Replay Queue ──
CREATE TABLE IF NOT EXISTS delivery_replay_queue (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    original_message_id VARCHAR(255) NOT NULL,
    campaign_id         VARCHAR(36),
    subscriber_id       VARCHAR(36),
    email               VARCHAR(320) NOT NULL,
    replay_reason       VARCHAR(100) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    priority            INT DEFAULT 5,
    scheduled_at        TIMESTAMPTZ,
    processed_at        TIMESTAMPTZ,
    error_message       TEXT,
    retry_count         INT DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_replay_queue_status ON delivery_replay_queue(status, priority, scheduled_at);
CREATE INDEX idx_replay_queue_tenant ON delivery_replay_queue(tenant_id, status);

-- ── Provider Health Status (Materialized) ──
CREATE TABLE IF NOT EXISTS provider_health_status (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    provider_id         VARCHAR(36) NOT NULL REFERENCES smtp_providers(id) ON DELETE CASCADE,
    current_status      VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    last_check_at       TIMESTAMPTZ,
    last_success_at     TIMESTAMPTZ,
    last_failure_at     TIMESTAMPTZ,
    consecutive_failures INT DEFAULT 0,
    circuit_breaker_open BOOLEAN DEFAULT FALSE,
    health_score        INT DEFAULT 100,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_provider_health_status UNIQUE (tenant_id, provider_id)
);

CREATE INDEX idx_health_status_provider ON provider_health_status(provider_id);
CREATE INDEX idx_health_status_unhealthy ON provider_health_status(tenant_id) WHERE current_status != 'HEALTHY';

-- ── Add health check fields to smtp_providers ──
ALTER TABLE smtp_providers ADD COLUMN IF NOT EXISTS health_check_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE smtp_providers ADD COLUMN IF NOT EXISTS health_check_url VARCHAR(500);
ALTER TABLE smtp_providers ADD COLUMN IF NOT EXISTS health_check_interval_seconds INT DEFAULT 60;
ALTER TABLE smtp_providers ADD COLUMN IF NOT EXISTS last_health_check_at TIMESTAMPTZ;
ALTER TABLE smtp_providers ADD COLUMN IF NOT EXISTS health_status VARCHAR(20) DEFAULT 'UNKNOWN';
