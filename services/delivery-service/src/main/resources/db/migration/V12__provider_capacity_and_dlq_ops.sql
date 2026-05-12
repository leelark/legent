-- V12: Phase 3 provider capacity, adaptive throttling, failover drills, DLQ operations

CREATE TABLE IF NOT EXISTS provider_capacity_profiles (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64) NOT NULL,
    provider_id             VARCHAR(64) NOT NULL,
    sender_domain           VARCHAR(255),
    isp_domain              VARCHAR(255),
    hourly_cap              INT NOT NULL DEFAULT 1000,
    daily_cap               INT NOT NULL DEFAULT 10000,
    current_max_per_minute  INT NOT NULL DEFAULT 60,
    min_success_rate        DOUBLE PRECISION NOT NULL DEFAULT 0.95,
    observed_success_rate   DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    bounce_rate             DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    complaint_rate          DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    backpressure_score      INT NOT NULL DEFAULT 0,
    failover_provider_id    VARCHAR(64),
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_evaluated_at       TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_provider_capacity_scope
    ON provider_capacity_profiles(tenant_id, workspace_id, provider_id, COALESCE(sender_domain, ''), COALESCE(isp_domain, ''))
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_provider_capacity_alerts
    ON provider_capacity_profiles(tenant_id, workspace_id, status, backpressure_score DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS provider_failover_tests (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64) NOT NULL,
    primary_provider_id     VARCHAR(64) NOT NULL,
    failover_provider_id    VARCHAR(64),
    status                  VARCHAR(32) NOT NULL,
    result_code             VARCHAR(64) NOT NULL,
    diagnostic              VARCHAR(2000),
    started_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_provider_failover_tests_scope
    ON provider_failover_tests(tenant_id, workspace_id, created_at DESC)
    WHERE deleted_at IS NULL;
