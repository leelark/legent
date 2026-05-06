-- V7: Admin settings engine scope expansion + tenant bootstrap status

ALTER TABLE system_configs
    ADD COLUMN IF NOT EXISTS module_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS scope_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS environment_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS dependency_keys JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS validation_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE system_configs
SET scope_type = CASE
    WHEN tenant_id IS NULL THEN 'GLOBAL'
    ELSE 'TENANT'
END
WHERE scope_type IS NULL;

UPDATE system_configs
SET module_key = LOWER(COALESCE(category, 'system'))
WHERE module_key IS NULL;

ALTER TABLE system_configs
    ALTER COLUMN scope_type SET NOT NULL;

ALTER TABLE system_configs
    ALTER COLUMN module_key SET NOT NULL;

ALTER TABLE system_configs
    DROP CONSTRAINT IF EXISTS uq_config_tenant_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_system_configs_scope_key
    ON system_configs(
        COALESCE(tenant_id, 'GLOBAL'),
        COALESCE(workspace_id, ''),
        COALESCE(environment_id, ''),
        config_key
    )
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_system_configs_scope
    ON system_configs(tenant_id, workspace_id, environment_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_system_configs_module
    ON system_configs(module_key)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS tenant_bootstrap_status (
    tenant_id        VARCHAR(36) PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    workspace_id     VARCHAR(64),
    environment_id   VARCHAR(64),
    status           VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    message          VARCHAR(1000),
    modules          JSONB NOT NULL DEFAULT '{}'::jsonb,
    retry_count      INT NOT NULL DEFAULT 0,
    last_attempt_at  TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bootstrap_status_state
    ON tenant_bootstrap_status(status, updated_at DESC);
