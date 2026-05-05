-- V8: widen identity bridge scoped identifiers for backward compatibility with existing IDs

ALTER TABLE account_memberships
    ALTER COLUMN user_id TYPE VARCHAR(36),
    ALTER COLUMN tenant_id TYPE VARCHAR(36),
    ALTER COLUMN workspace_id TYPE VARCHAR(36),
    ALTER COLUMN team_id TYPE VARCHAR(36);

ALTER TABLE account_role_bindings
    ALTER COLUMN scope_id TYPE VARCHAR(36);

ALTER TABLE auth_invitations
    ALTER COLUMN tenant_id TYPE VARCHAR(36),
    ALTER COLUMN workspace_id TYPE VARCHAR(36),
    ALTER COLUMN invited_by_user_id TYPE VARCHAR(36);

ALTER TABLE account_sessions
    ALTER COLUMN tenant_id TYPE VARCHAR(36),
    ALTER COLUMN workspace_id TYPE VARCHAR(36),
    ALTER COLUMN environment_id TYPE VARCHAR(36);
