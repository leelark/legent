-- V6: Platform Core Foundation (enterprise hierarchy, access, quotas, environments)

-- Organization root (tenant_id remains canonical organization id for backward compatibility)
CREATE TABLE IF NOT EXISTS organizations (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    slug                VARCHAR(128) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_organizations_slug_active ON organizations(slug) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS business_units (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    organization_id     VARCHAR(26) NOT NULL REFERENCES organizations(id),
    parent_id           VARCHAR(26) REFERENCES business_units(id),
    code                VARCHAR(64),
    name                VARCHAR(255) NOT NULL,
    description         VARCHAR(1000),
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_bu_tenant ON business_units(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_bu_org ON business_units(organization_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS workspaces (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    organization_id     VARCHAR(26) NOT NULL REFERENCES organizations(id),
    business_unit_id    VARCHAR(26) REFERENCES business_units(id),
    name                VARCHAR(255) NOT NULL,
    slug                VARCHAR(128) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    default_environment VARCHAR(32) NOT NULL DEFAULT 'PRODUCTION',
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_workspaces_tenant ON workspaces(tenant_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_workspace_slug_per_tenant ON workspaces(tenant_id, slug) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS teams (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26) NOT NULL REFERENCES workspaces(id),
    name                VARCHAR(255) NOT NULL,
    code                VARCHAR(64),
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_teams_workspace ON teams(workspace_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS departments (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26) NOT NULL REFERENCES workspaces(id),
    name                VARCHAR(255) NOT NULL,
    code                VARCHAR(64),
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_departments_workspace ON departments(workspace_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS membership_links (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    user_id             VARCHAR(26) NOT NULL,
    organization_id     VARCHAR(26) NOT NULL REFERENCES organizations(id),
    business_unit_id    VARCHAR(26) REFERENCES business_units(id),
    workspace_id        VARCHAR(26) REFERENCES workspaces(id),
    team_id             VARCHAR(26) REFERENCES teams(id),
    department_id       VARCHAR(26) REFERENCES departments(id),
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    principal_type      VARCHAR(32) NOT NULL DEFAULT 'USER',
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_memberships_tenant_user ON membership_links(tenant_id, user_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS ownership_transfers (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    resource_type       VARCHAR(64) NOT NULL,
    resource_id         VARCHAR(26) NOT NULL,
    from_owner_id       VARCHAR(26) NOT NULL,
    to_owner_id         VARCHAR(26) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    notes               VARCHAR(2000),
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_ownership_transfers_tenant ON ownership_transfers(tenant_id, resource_type, resource_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS shared_resources (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    resource_type       VARCHAR(64) NOT NULL,
    resource_id         VARCHAR(26) NOT NULL,
    owner_workspace_id  VARCHAR(26) REFERENCES workspaces(id),
    visibility_scope    VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE',
    shared_with         JSONB,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_shared_resources_tenant ON shared_resources(tenant_id, resource_type) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS role_definitions (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26),
    role_key            VARCHAR(128) NOT NULL,
    display_name        VARCHAR(255) NOT NULL,
    description         VARCHAR(1000),
    is_system           BOOLEAN NOT NULL DEFAULT FALSE,
    permissions         JSONB NOT NULL DEFAULT '[]',
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_role_definitions_scope_key ON role_definitions(COALESCE(tenant_id, 'GLOBAL'), role_key) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS permission_groups (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26),
    group_key           VARCHAR(128) NOT NULL,
    display_name        VARCHAR(255) NOT NULL,
    permissions         JSONB NOT NULL DEFAULT '[]',
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_permission_groups_scope_key ON permission_groups(COALESCE(tenant_id, 'GLOBAL'), group_key) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS role_permissions (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26),
    role_definition_id  VARCHAR(26) NOT NULL REFERENCES role_definitions(id) ON DELETE CASCADE,
    permission_key      VARCHAR(255) NOT NULL,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions(role_definition_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS principal_role_bindings (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    principal_type      VARCHAR(32) NOT NULL,
    principal_id        VARCHAR(26) NOT NULL,
    role_definition_id  VARCHAR(26) REFERENCES role_definitions(id),
    permission_group_id VARCHAR(26) REFERENCES permission_groups(id),
    workspace_id        VARCHAR(26) REFERENCES workspaces(id),
    team_id             VARCHAR(26) REFERENCES teams(id),
    resource_type       VARCHAR(64),
    resource_id         VARCHAR(26),
    effective_from      TIMESTAMPTZ,
    effective_until     TIMESTAMPTZ,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_role_bindings_tenant_principal ON principal_role_bindings(tenant_id, principal_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS delegated_access_grants (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    grantor_user_id     VARCHAR(26) NOT NULL,
    grantee_user_id     VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26) REFERENCES workspaces(id),
    permissions         JSONB NOT NULL DEFAULT '[]',
    reason              VARCHAR(1000),
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    expires_at          TIMESTAMPTZ,
    approved_by         VARCHAR(26),
    approved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_access_grants_tenant_grantee ON delegated_access_grants(tenant_id, grantee_user_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS privileged_access_requests (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    requester_user_id   VARCHAR(26) NOT NULL,
    approver_user_id    VARCHAR(26),
    requested_permissions JSONB NOT NULL DEFAULT '[]',
    reason              VARCHAR(1000),
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    decision_note       VARCHAR(1000),
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tenant_lifecycle_state (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL UNIQUE,
    state               VARCHAR(32) NOT NULL,
    trial_ends_at       TIMESTAMPTZ,
    activated_at        TIMESTAMPTZ,
    restricted_at       TIMESTAMPTZ,
    suspended_at        TIMESTAMPTZ,
    expired_at          TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    archived_at         TIMESTAMPTZ,
    reason              VARCHAR(1000),
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS subscription_plans (
    id                  VARCHAR(26) PRIMARY KEY,
    plan_key            VARCHAR(128) NOT NULL UNIQUE,
    display_name        VARCHAR(255) NOT NULL,
    monthly_price       NUMERIC(12,2),
    yearly_price        NUMERIC(12,2),
    limits              JSONB,
    features            JSONB,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tenant_subscriptions (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    plan_id             VARCHAR(26) NOT NULL REFERENCES subscription_plans(id),
    status              VARCHAR(32) NOT NULL DEFAULT 'TRIAL',
    billing_cycle       VARCHAR(16) NOT NULL DEFAULT 'MONTHLY',
    starts_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ends_at             TIMESTAMPTZ,
    auto_renew          BOOLEAN NOT NULL DEFAULT TRUE,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_tenant_subscriptions_tenant ON tenant_subscriptions(tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS plan_features (
    id                  VARCHAR(26) PRIMARY KEY,
    plan_id             VARCHAR(26) NOT NULL REFERENCES subscription_plans(id) ON DELETE CASCADE,
    feature_key         VARCHAR(255) NOT NULL,
    is_enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS addons (
    id                  VARCHAR(26) PRIMARY KEY,
    addon_key           VARCHAR(128) NOT NULL UNIQUE,
    display_name        VARCHAR(255) NOT NULL,
    unit_price          NUMERIC(12,2),
    metadata            JSONB,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS quota_policies (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26) REFERENCES workspaces(id),
    metric_key          VARCHAR(128) NOT NULL,
    soft_limit          BIGINT,
    hard_limit          BIGINT,
    overage_rate        NUMERIC(12,4),
    is_enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_quota_scope_metric ON quota_policies(tenant_id, COALESCE(workspace_id, ''), metric_key) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS usage_counters (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26),
    metric_key          VARCHAR(128) NOT NULL,
    period_start        DATE NOT NULL,
    period_end          DATE NOT NULL,
    value               BIGINT NOT NULL DEFAULT 0,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_usage_counter_scope ON usage_counters(tenant_id, COALESCE(workspace_id, ''), metric_key, period_start, period_end) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS usage_rollups (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26),
    metric_key          VARCHAR(128) NOT NULL,
    bucket              VARCHAR(16) NOT NULL,
    bucket_start        TIMESTAMPTZ NOT NULL,
    value               BIGINT NOT NULL DEFAULT 0,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS overage_records (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26),
    metric_key          VARCHAR(128) NOT NULL,
    quantity            BIGINT NOT NULL,
    unit_rate           NUMERIC(12,4) NOT NULL,
    amount              NUMERIC(12,2) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS usage_forecasts (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26),
    metric_key          VARCHAR(128) NOT NULL,
    forecast_period     DATE NOT NULL,
    projected_value     BIGINT NOT NULL,
    confidence_score    NUMERIC(5,2),
    model_version       VARCHAR(64),
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS environments (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26) NOT NULL REFERENCES workspaces(id),
    environment_key     VARCHAR(32) NOT NULL,
    display_name        VARCHAR(64) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    is_locked           BOOLEAN NOT NULL DEFAULT FALSE,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_environment_scope ON environments(tenant_id, workspace_id, environment_key) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS environment_configs (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26) NOT NULL,
    environment_id      VARCHAR(26) NOT NULL REFERENCES environments(id),
    config_key          VARCHAR(255) NOT NULL,
    config_value        JSONB,
    inherited_from      VARCHAR(26),
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_environment_config_key ON environment_configs(environment_id, config_key) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS promotion_requests (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26) NOT NULL,
    from_environment_id VARCHAR(26) NOT NULL REFERENCES environments(id),
    to_environment_id   VARCHAR(26) NOT NULL REFERENCES environments(id),
    requested_by        VARCHAR(26) NOT NULL,
    approved_by         VARCHAR(26),
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    summary             VARCHAR(1000),
    changeset           JSONB,
    approved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_promotion_requests_scope ON promotion_requests(tenant_id, workspace_id, status) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS environment_locks (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26) NOT NULL,
    environment_id      VARCHAR(26) NOT NULL REFERENCES environments(id),
    lock_type           VARCHAR(64) NOT NULL,
    locked_by           VARCHAR(26) NOT NULL,
    reason              VARCHAR(1000),
    expires_at          TIMESTAMPTZ,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS isolation_policies (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26),
    environment_id      VARCHAR(26),
    db_namespace        VARCHAR(255),
    queue_namespace     VARCHAR(255),
    storage_namespace   VARCHAR(255),
    api_namespace       VARCHAR(255),
    event_namespace     VARCHAR(255),
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS feature_controls (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26),
    feature_key         VARCHAR(255) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    source              VARCHAR(32) NOT NULL DEFAULT 'TENANT',
    dependency_keys     JSONB,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_feature_controls_scope ON feature_controls(tenant_id, COALESCE(workspace_id, ''), feature_key) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS core_audit_events (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26),
    environment_id      VARCHAR(26),
    actor_id            VARCHAR(26),
    action              VARCHAR(128) NOT NULL,
    resource_type       VARCHAR(128) NOT NULL,
    resource_id         VARCHAR(26),
    ownership_scope     VARCHAR(64),
    request_id          VARCHAR(64),
    correlation_id      VARCHAR(64),
    status              VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    details             JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_core_audit_events_tenant ON core_audit_events(tenant_id, created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS core_invitations (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    invited_by          VARCHAR(26),
    organization_id     VARCHAR(26) REFERENCES organizations(id),
    workspace_id        VARCHAR(26) REFERENCES workspaces(id),
    role_keys           JSONB NOT NULL DEFAULT '[]',
    token               VARCHAR(128) NOT NULL UNIQUE,
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
CREATE INDEX IF NOT EXISTS idx_core_invitations_email ON core_invitations(LOWER(email), status) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS idempotency_records (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26),
    idempotency_key     VARCHAR(128) NOT NULL,
    operation           VARCHAR(255) NOT NULL,
    resource_id         VARCHAR(26),
    response_hash       VARCHAR(255),
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_idempotency_operation ON idempotency_records(COALESCE(tenant_id, 'GLOBAL'), idempotency_key, operation) WHERE deleted_at IS NULL;

INSERT INTO subscription_plans (id, plan_key, display_name, monthly_price, yearly_price, limits, features, status)
VALUES
    ('01KQPLANSTARTER00000000001', 'STARTER', 'Starter', 29.00, 290.00,
     '{"contacts":10000,"emails_sent":100000,"api_calls":250000}'::jsonb,
     '["core.dashboard","core.templates","core.campaigns"]'::jsonb,
     'ACTIVE'),
    ('01KQPLANGROWTH000000000001', 'GROWTH', 'Growth', 99.00, 990.00,
     '{"contacts":100000,"emails_sent":1000000,"api_calls":2000000}'::jsonb,
     '["core.dashboard","core.templates","core.campaigns","core.automation","core.analytics"]'::jsonb,
     'ACTIVE')
ON CONFLICT (plan_key) DO NOTHING;

INSERT INTO role_definitions (id, tenant_id, role_key, display_name, description, is_system, permissions)
VALUES
    ('01KQROLEPLATADMIN000000001', NULL, 'PLATFORM_ADMIN', 'Platform Admin', 'Global platform administration role', TRUE,
     '["*"]'::jsonb),
    ('01KQROLEORGADMIN0000000001', NULL, 'ORG_ADMIN', 'Organization Admin', 'Tenant administration role', TRUE,
     '["tenant:*","workspace:*","team:*","role:*","quota:*","billing:*","audit:*","feature:*"]'::jsonb),
    ('01KQROLEWORKOWNER000000001', NULL, 'WORKSPACE_OWNER', 'Workspace Owner', 'Workspace-level ownership role', TRUE,
     '["workspace:*","team:*","campaign:*","template:*","audience:*","automation:*","analytics:read"]'::jsonb)
ON CONFLICT DO NOTHING;

INSERT INTO permission_groups (id, tenant_id, group_key, display_name, permissions)
VALUES
    ('01KQGROUPREADONLY000000001', NULL, 'READ_ONLY', 'Read Only', '["*:read"]'::jsonb),
    ('01KQGROUPOPERATIONS0000001', NULL, 'OPERATIONS', 'Operations', '["delivery:*","tracking:*","deliverability:*","notification:*"]'::jsonb)
ON CONFLICT DO NOTHING;
