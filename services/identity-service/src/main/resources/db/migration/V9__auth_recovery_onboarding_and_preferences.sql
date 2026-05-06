-- V9: frontend studio auth recovery, onboarding and user preferences

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id              VARCHAR(26) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email           VARCHAR(255) NOT NULL,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(26),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_lookup
    ON password_reset_tokens(tenant_id, user_id, expires_at)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS user_preferences (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    user_id             VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ui_mode             VARCHAR(16) NOT NULL DEFAULT 'BASIC',
    theme               VARCHAR(16) NOT NULL DEFAULT 'light',
    density             VARCHAR(16) NOT NULL DEFAULT 'comfortable',
    sidebar_collapsed   BOOLEAN NOT NULL DEFAULT FALSE,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_preferences_scope
    ON user_preferences(tenant_id, user_id)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS onboarding_states (
    id              VARCHAR(26) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id    VARCHAR(36),
    status          VARCHAR(32) NOT NULL DEFAULT 'STARTED',
    step_key        VARCHAR(64) NOT NULL DEFAULT 'workspace',
    payload         JSONB,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(26),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_onboarding_state_scope
    ON onboarding_states(tenant_id, user_id)
    WHERE deleted_at IS NULL;
