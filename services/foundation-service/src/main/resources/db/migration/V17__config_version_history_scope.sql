-- Add nullable workspace/environment scope to config version history.
-- Existing rows are intentionally left unchanged; callers must opt into exact nullable scope.

ALTER TABLE config_version_history
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64);

ALTER TABLE config_version_history
    ADD COLUMN IF NOT EXISTS environment_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_config_version_exact_scope_changed_at
    ON config_version_history(tenant_id, workspace_id, environment_id, changed_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_config_version_exact_scope_key_version
    ON config_version_history(tenant_id, workspace_id, environment_id, config_key, version) NULLS NOT DISTINCT;
