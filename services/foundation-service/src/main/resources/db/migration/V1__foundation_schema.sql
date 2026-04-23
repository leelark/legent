-- =============================================
-- Foundation Service Database Schema
-- Version: V1
-- Multi-tenant tables with tenant_id isolation
-- =============================================

-- ── Tenants ──
CREATE TABLE IF NOT EXISTS tenants (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(128) NOT NULL UNIQUE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    plan            VARCHAR(50) NOT NULL DEFAULT 'STARTER',
    settings        JSONB DEFAULT '{}',
    branding        JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(36),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_tenants_slug ON tenants(slug);
CREATE INDEX idx_tenants_status ON tenants(status) WHERE deleted_at IS NULL;

-- ── System Configuration ──
CREATE TABLE IF NOT EXISTS system_configs (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) REFERENCES tenants(id),
    config_key      VARCHAR(128) NOT NULL,
    config_value    TEXT NOT NULL,
    value_type      VARCHAR(20) NOT NULL DEFAULT 'STRING',
    category        VARCHAR(64) NOT NULL DEFAULT 'GENERAL',
    description     VARCHAR(500),
    is_encrypted    BOOLEAN NOT NULL DEFAULT FALSE,
    is_system       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(36),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_config_tenant_key UNIQUE (tenant_id, config_key)
);

CREATE INDEX idx_sys_config_tenant ON system_configs(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_config_category ON system_configs(category) WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_config_key ON system_configs(config_key) WHERE deleted_at IS NULL;

-- ── Feature Flags ──
CREATE TABLE IF NOT EXISTS feature_flags (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) REFERENCES tenants(id),
    flag_key        VARCHAR(128) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    description     VARCHAR(500),
    scope           VARCHAR(20) NOT NULL DEFAULT 'GLOBAL',
    rules           JSONB DEFAULT '[]',
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(36),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_flag_tenant_key UNIQUE (tenant_id, flag_key)
);

CREATE INDEX idx_feature_flags_tenant ON feature_flags(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_feature_flags_key ON feature_flags(flag_key) WHERE deleted_at IS NULL;
CREATE INDEX idx_feature_flags_scope ON feature_flags(scope) WHERE deleted_at IS NULL;

-- ── Environment Settings ──
CREATE TABLE IF NOT EXISTS environment_settings (
    id              VARCHAR(36) PRIMARY KEY,
    environment     VARCHAR(20) NOT NULL,
    setting_key     VARCHAR(128) NOT NULL,
    setting_value   TEXT NOT NULL,
    description     VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(36),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_env_setting UNIQUE (environment, setting_key)
);

CREATE INDEX idx_env_settings_env ON environment_settings(environment);

-- ── Provider Configuration (SMTP/Gateway/DNS toggle) ──
CREATE TABLE IF NOT EXISTS provider_configs (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) REFERENCES tenants(id),
    provider_type   VARCHAR(30) NOT NULL,
    provider_name   VARCHAR(50) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    config          JSONB NOT NULL DEFAULT '{}',
    priority        INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(36),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_provider_tenant_type UNIQUE (tenant_id, provider_type, provider_name)
);

CREATE INDEX idx_provider_tenant ON provider_configs(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_provider_type ON provider_configs(provider_type) WHERE deleted_at IS NULL;

-- ── System Metadata / Audit Log ──
CREATE TABLE IF NOT EXISTS system_events (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36),
    event_type      VARCHAR(64) NOT NULL,
    event_source    VARCHAR(64) NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}',
    correlation_id  VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sys_events_type ON system_events(event_type);
CREATE INDEX idx_sys_events_tenant ON system_events(tenant_id);
CREATE INDEX idx_sys_events_created ON system_events(created_at);

-- ── Seed: Global default configs ──
INSERT INTO system_configs (id, tenant_id, config_key, config_value, value_type, category, description, is_system)
VALUES
    ('01HDEFAULT000001', NULL, 'smtp.provider', 'postal', 'STRING', 'DELIVERY', 'Default SMTP provider', TRUE),
    ('01HDEFAULT000002', NULL, 'gateway.provider', 'nginx', 'STRING', 'INFRASTRUCTURE', 'Default API gateway', TRUE),
    ('01HDEFAULT000003', NULL, 'dns.provider', 'powerdns', 'STRING', 'INFRASTRUCTURE', 'Default DNS provider', TRUE),
    ('01HDEFAULT000004', NULL, 'email.batch_size', '1000', 'INTEGER', 'DELIVERY', 'Default batch size for email sends', TRUE),
    ('01HDEFAULT000005', NULL, 'email.rate_limit_per_second', '100', 'INTEGER', 'DELIVERY', 'Default emails per second limit', TRUE)
ON CONFLICT DO NOTHING;
