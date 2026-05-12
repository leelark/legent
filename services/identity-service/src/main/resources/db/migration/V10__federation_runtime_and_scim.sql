-- V10: Runtime SAML/OIDC federation broker and SCIM v2 provisioning server.

CREATE TABLE IF NOT EXISTS federated_identity_providers (
    id                              VARCHAR(26) PRIMARY KEY,
    tenant_id                       VARCHAR(64) NOT NULL,
    provider_key                    VARCHAR(128) NOT NULL,
    display_name                    VARCHAR(255) NOT NULL,
    protocol                        VARCHAR(16) NOT NULL,
    status                          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    issuer                          VARCHAR(500),
    client_id                       VARCHAR(255),
    client_secret_ref               VARCHAR(500),
    authorization_endpoint          VARCHAR(1000),
    token_endpoint                  VARCHAR(1000),
    userinfo_endpoint               VARCHAR(1000),
    jwks_url                        VARCHAR(1000),
    redirect_uri                    VARCHAR(1000),
    scopes                          JSONB NOT NULL DEFAULT '[]',
    entity_id                       VARCHAR(500),
    sso_url                         VARCHAR(1000),
    audience                        VARCHAR(500),
    signing_certificate             TEXT,
    jit_provisioning_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    scim_enabled                    BOOLEAN NOT NULL DEFAULT FALSE,
    default_workspace_id            VARCHAR(64),
    default_role_keys               JSONB NOT NULL DEFAULT '["USER"]',
    attribute_mapping               JSONB NOT NULL DEFAULT '{}',
    metadata                        JSONB NOT NULL DEFAULT '{}',
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                      VARCHAR(26),
    deleted_at                      TIMESTAMPTZ,
    version                         BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_federated_identity_provider_key
    ON federated_identity_providers(tenant_id, provider_key)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_federated_identity_provider_status
    ON federated_identity_providers(tenant_id, protocol, status)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS federation_login_states (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    provider_id         VARCHAR(26) NOT NULL REFERENCES federated_identity_providers(id) ON DELETE CASCADE,
    protocol            VARCHAR(16) NOT NULL,
    request_id          VARCHAR(128),
    state               VARCHAR(256) NOT NULL,
    nonce               VARCHAR(256),
    code_verifier       VARCHAR(256),
    redirect_after      VARCHAR(1000),
    expires_at          TIMESTAMPTZ NOT NULL,
    consumed_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_federation_login_state
    ON federation_login_states(state)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_federation_login_state_request
    ON federation_login_states(tenant_id, provider_id, request_id)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS federation_scim_tokens (
    id                          VARCHAR(26) PRIMARY KEY,
    tenant_id                   VARCHAR(64) NOT NULL,
    provider_id                 VARCHAR(26) NOT NULL REFERENCES federated_identity_providers(id) ON DELETE CASCADE,
    label                       VARCHAR(255) NOT NULL,
    token_hash                  VARCHAR(64) NOT NULL,
    scopes                      JSONB NOT NULL DEFAULT '["scim:users","scim:groups"]',
    status                      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    expires_at                  TIMESTAMPTZ,
    last_used_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(26),
    deleted_at                  TIMESTAMPTZ,
    version                     BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_federation_scim_token_hash
    ON federation_scim_tokens(token_hash)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_federation_scim_tokens_provider
    ON federation_scim_tokens(tenant_id, provider_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS federation_scim_groups (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    provider_id         VARCHAR(26) NOT NULL REFERENCES federated_identity_providers(id) ON DELETE CASCADE,
    external_id         VARCHAR(255),
    display_name        VARCHAR(255) NOT NULL,
    role_key            VARCHAR(128),
    members             JSONB NOT NULL DEFAULT '[]',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_federation_scim_group_name
    ON federation_scim_groups(tenant_id, provider_id, lower(display_name))
    WHERE deleted_at IS NULL;

ALTER TABLE users ADD COLUMN IF NOT EXISTS external_id VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS identity_provider_id VARCHAR(26);
CREATE INDEX IF NOT EXISTS idx_users_federated_external
    ON users(tenant_id, identity_provider_id, external_id)
    WHERE deleted_at IS NULL AND external_id IS NOT NULL;
