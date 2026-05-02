-- =============================================
-- Tenant Audit and Config Versioning Schema
-- Version: V4
-- Adds audit trail and config versioning support
-- =============================================

-- ── Add new columns to tenants table ──
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMPTZ;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS suspension_reason VARCHAR(500);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;

-- ── Tenant Audit Log ──
CREATE TABLE IF NOT EXISTS tenant_audit_log (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) REFERENCES tenants(id) ON DELETE CASCADE,
    action          VARCHAR(50) NOT NULL,
    action_type     VARCHAR(50) NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       VARCHAR(36),
    old_values      JSONB,
    new_values      JSONB,
    metadata        JSONB DEFAULT '{}',
    performed_by    VARCHAR(36),
    performed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address      VARCHAR(45),
    user_agent      TEXT
);

CREATE INDEX IF NOT EXISTS idx_tenant_audit_tenant ON tenant_audit_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_audit_action ON tenant_audit_log(action);
CREATE INDEX IF NOT EXISTS idx_tenant_audit_performed_at ON tenant_audit_log(performed_at);
CREATE INDEX IF NOT EXISTS idx_tenant_audit_entity ON tenant_audit_log(entity_type, entity_id);

-- ── Config Version History ──
CREATE TABLE IF NOT EXISTS config_version_history (
    id              VARCHAR(36) PRIMARY KEY,
    tenant_id       VARCHAR(36) REFERENCES tenants(id) ON DELETE CASCADE,
    config_key      VARCHAR(128) NOT NULL,
    config_value    TEXT NOT NULL,
    value_type      VARCHAR(20) NOT NULL DEFAULT 'STRING',
    category        VARCHAR(64) NOT NULL DEFAULT 'GENERAL',
    description     VARCHAR(500),
    is_encrypted    BOOLEAN NOT NULL DEFAULT FALSE,
    version         INT NOT NULL,
    change_type     VARCHAR(20) NOT NULL,
    changed_by      VARCHAR(36),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    rollback_to_version INT
);

CREATE INDEX IF NOT EXISTS idx_config_version_tenant ON config_version_history(tenant_id);
CREATE INDEX IF NOT EXISTS idx_config_version_key ON config_version_history(config_key);
CREATE INDEX IF NOT EXISTS idx_config_version_tenant_key ON config_version_history(tenant_id, config_key);
CREATE INDEX IF NOT EXISTS idx_config_version_changed_at ON config_version_history(changed_at);

-- ── System Config Version Tracking ──
ALTER TABLE system_configs ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;
ALTER TABLE system_configs ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(36);

-- ── Feature Flag Version History ──
CREATE TABLE IF NOT EXISTS feature_flag_history (
    id              VARCHAR(36) PRIMARY KEY,
    flag_id         VARCHAR(36) REFERENCES feature_flags(id) ON DELETE CASCADE,
    tenant_id       VARCHAR(36) REFERENCES tenants(id) ON DELETE CASCADE,
    flag_key        VARCHAR(128) NOT NULL,
    enabled         BOOLEAN NOT NULL,
    description     VARCHAR(500),
    scope           VARCHAR(20) NOT NULL,
    rules           JSONB DEFAULT '[]',
    metadata        JSONB DEFAULT '{}',
    version         INT NOT NULL,
    change_type     VARCHAR(20) NOT NULL,
    changed_by      VARCHAR(36),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_flag_history_flag ON feature_flag_history(flag_id);
CREATE INDEX IF NOT EXISTS idx_flag_history_tenant ON feature_flag_history(tenant_id);
CREATE INDEX IF NOT EXISTS idx_flag_history_key ON feature_flag_history(flag_key);

-- ── Provider Config Version History ──
CREATE TABLE IF NOT EXISTS provider_config_history (
    id              VARCHAR(36) PRIMARY KEY,
    provider_id     VARCHAR(36) REFERENCES provider_configs(id) ON DELETE CASCADE,
    tenant_id       VARCHAR(36) REFERENCES tenants(id) ON DELETE CASCADE,
    provider_type   VARCHAR(30) NOT NULL,
    provider_name   VARCHAR(50) NOT NULL,
    config          JSONB NOT NULL,
    is_active       BOOLEAN NOT NULL,
    priority        INT NOT NULL,
    version         INT NOT NULL,
    change_type     VARCHAR(20) NOT NULL,
    changed_by      VARCHAR(36),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_provider_history_provider ON provider_config_history(provider_id);
CREATE INDEX IF NOT EXISTS idx_provider_history_tenant ON provider_config_history(tenant_id);

-- ── System Events Enhancement ──
ALTER TABLE system_events ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);
ALTER TABLE system_events ADD COLUMN IF NOT EXISTS user_agent TEXT;

-- ── Session/Token Tracking for Revocation ──
CREATE TABLE IF NOT EXISTS user_sessions (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL,
    tenant_id       VARCHAR(36) REFERENCES tenants(id) ON DELETE CASCADE,
    session_token   VARCHAR(255) NOT NULL,
    is_revoked      BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMPTZ,
    revoked_reason  VARCHAR(255),
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_accessed_at TIMESTAMPTZ,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    device_info     JSONB
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_user ON user_sessions(user_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_token ON user_sessions(session_token);
CREATE INDEX IF NOT EXISTS idx_user_sessions_revoked ON user_sessions(is_revoked) WHERE is_revoked = FALSE;

-- ── Add indexes for better performance ──
CREATE INDEX IF NOT EXISTS idx_tenants_suspended ON tenants(suspended_at) WHERE suspended_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tenants_archived ON tenants(archived_at) WHERE archived_at IS NOT NULL;
