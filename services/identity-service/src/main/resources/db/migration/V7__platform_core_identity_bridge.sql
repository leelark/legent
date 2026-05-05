-- V7: Identity platform-core bridge (global accounts, memberships, multi-role bindings)

CREATE TABLE IF NOT EXISTS accounts (
    id                  VARCHAR(26) PRIMARY KEY,
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    first_name          VARCHAR(100),
    last_name           VARCHAR(100),
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_accounts_email_active ON accounts(LOWER(email)) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS account_memberships (
    id                  VARCHAR(26) PRIMARY KEY,
    account_id          VARCHAR(26) NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    user_id             VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    tenant_id           VARCHAR(36) NOT NULL,
    workspace_id        VARCHAR(36),
    team_id             VARCHAR(36),
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    is_default          BOOLEAN NOT NULL DEFAULT FALSE,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_account_membership_scope ON account_memberships(account_id, tenant_id, COALESCE(workspace_id, '')) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_account_memberships_user ON account_memberships(user_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS account_role_bindings (
    id                  VARCHAR(26) PRIMARY KEY,
    account_id          VARCHAR(26) NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    membership_id       VARCHAR(26) NOT NULL REFERENCES account_memberships(id) ON DELETE CASCADE,
    role_key            VARCHAR(128) NOT NULL,
    scope_type          VARCHAR(32) NOT NULL DEFAULT 'TENANT',
    scope_id            VARCHAR(36),
    effective_from      TIMESTAMPTZ,
    effective_until     TIMESTAMPTZ,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_account_role_bindings_membership ON account_role_bindings(membership_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS auth_invitations (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    workspace_id        VARCHAR(36),
    email               VARCHAR(255) NOT NULL,
    token               VARCHAR(128) NOT NULL UNIQUE,
    role_keys           JSONB NOT NULL DEFAULT '[]',
    invited_by_user_id  VARCHAR(36),
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    expires_at          TIMESTAMPTZ,
    accepted_at         TIMESTAMPTZ,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_auth_invitations_email ON auth_invitations(LOWER(email), status) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS account_sessions (
    id                  VARCHAR(26) PRIMARY KEY,
    account_id          VARCHAR(26) NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    tenant_id           VARCHAR(36) NOT NULL,
    workspace_id        VARCHAR(36),
    environment_id      VARCHAR(36),
    refresh_token_hash  VARCHAR(64),
    user_agent          TEXT,
    ip_address          VARCHAR(45),
    last_active_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at          TIMESTAMPTZ,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_account_sessions_account ON account_sessions(account_id, tenant_id) WHERE deleted_at IS NULL;

INSERT INTO accounts (id, email, password_hash, first_name, last_name, status, created_at, updated_at)
SELECT
    '01KQACCTDEFADMIN0000000001',
    'admin@legent.com',
    '$2b$10$uljUCrJIHC0EFF8t4ZMkUeClESdARKImtgPJKniwGmQ1Yj2lwDLee',
    'Admin',
    'User',
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE LOWER(email) = 'admin@legent.com');

INSERT INTO account_memberships (id, account_id, user_id, tenant_id, status, is_default, created_at, updated_at)
SELECT
    '01KQMEMBDEFADMIN0000000001',
    a.id,
    u.id,
    u.tenant_id,
    'ACTIVE',
    TRUE,
    NOW(),
    NOW()
FROM accounts a
JOIN users u ON LOWER(u.email) = LOWER(a.email)
WHERE LOWER(a.email) = 'admin@legent.com'
  AND NOT EXISTS (
      SELECT 1 FROM account_memberships m WHERE m.account_id = a.id AND m.tenant_id = u.tenant_id AND m.deleted_at IS NULL
  );

INSERT INTO account_role_bindings (id, account_id, membership_id, role_key, scope_type, created_at, updated_at)
SELECT
    '01KQROLEDEFADMIN0000000001',
    m.account_id,
    m.id,
    'ADMIN',
    'TENANT',
    NOW(),
    NOW()
FROM account_memberships m
WHERE m.is_default = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM account_role_bindings rb WHERE rb.membership_id = m.id AND rb.role_key = 'ADMIN' AND rb.deleted_at IS NULL
  );
