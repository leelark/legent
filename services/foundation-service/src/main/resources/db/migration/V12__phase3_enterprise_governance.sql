-- V12: Phase 3 enterprise governance, SSO/SCIM, immutable evidence, retention, consent, privacy

ALTER TABLE business_units ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE business_units ADD COLUMN IF NOT EXISTS path VARCHAR(1000);
ALTER TABLE business_units ADD COLUMN IF NOT EXISTS depth INT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_business_unit_code_scope
    ON business_units(tenant_id, organization_id, COALESCE(parent_id, ''), code)
    WHERE deleted_at IS NULL AND code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_business_units_parent
    ON business_units(tenant_id, parent_id)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_business_units_path
    ON business_units(tenant_id, path)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS enterprise_identity_providers (
    id                          VARCHAR(26) PRIMARY KEY,
    tenant_id                   VARCHAR(26) NOT NULL,
    provider_key                VARCHAR(128) NOT NULL,
    display_name                VARCHAR(255) NOT NULL,
    protocol                    VARCHAR(16) NOT NULL,
    status                      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    issuer                      VARCHAR(500),
    entity_id                   VARCHAR(500),
    sso_url                     VARCHAR(1000),
    jwks_url                    VARCHAR(1000),
    metadata_url                VARCHAR(1000),
    attribute_mapping           JSONB NOT NULL DEFAULT '{}',
    certificate_fingerprint     VARCHAR(255),
    signing_certificate         TEXT,
    scim_enabled                BOOLEAN NOT NULL DEFAULT FALSE,
    jit_provisioning_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    default_role_keys           JSONB NOT NULL DEFAULT '[]',
    metadata                    JSONB NOT NULL DEFAULT '{}',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(26),
    deleted_at                  TIMESTAMPTZ,
    version                     BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_enterprise_idp_key
    ON enterprise_identity_providers(tenant_id, provider_key)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_enterprise_idp_tenant_protocol
    ON enterprise_identity_providers(tenant_id, protocol, status)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS scim_tokens (
    id                          VARCHAR(26) PRIMARY KEY,
    tenant_id                   VARCHAR(26) NOT NULL,
    identity_provider_id        VARCHAR(26) NOT NULL REFERENCES enterprise_identity_providers(id) ON DELETE CASCADE,
    label                       VARCHAR(255) NOT NULL,
    token_hash                  VARCHAR(64) NOT NULL,
    scopes                      JSONB NOT NULL DEFAULT '[]',
    status                      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    expires_at                  TIMESTAMPTZ,
    last_used_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(26),
    deleted_at                  TIMESTAMPTZ,
    version                     BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_scim_token_hash
    ON scim_tokens(token_hash)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_scim_tokens_tenant
    ON scim_tokens(tenant_id, identity_provider_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS immutable_audit_evidence (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26),
    event_type          VARCHAR(128) NOT NULL,
    resource_type       VARCHAR(128) NOT NULL,
    resource_id         VARCHAR(128),
    actor_id            VARCHAR(26),
    payload             JSONB NOT NULL DEFAULT '{}',
    previous_hash       VARCHAR(64),
    event_hash          VARCHAR(64) NOT NULL,
    hash_algorithm      VARCHAR(32) NOT NULL DEFAULT 'SHA-256',
    evidence_time       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sealed_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    retention_category  VARCHAR(64) NOT NULL DEFAULT 'STANDARD',
    legal_hold          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_immutable_audit_hash
    ON immutable_audit_evidence(tenant_id, event_hash)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_immutable_audit_scope
    ON immutable_audit_evidence(tenant_id, workspace_id, evidence_time DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS retention_matrix (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26),
    data_domain         VARCHAR(128) NOT NULL,
    resource_type       VARCHAR(128) NOT NULL,
    retention_days      INT NOT NULL,
    disposition         VARCHAR(32) NOT NULL DEFAULT 'DELETE',
    legal_basis         VARCHAR(128) NOT NULL,
    policy_version      VARCHAR(64) NOT NULL DEFAULT 'v1',
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    metadata            JSONB NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_retention_matrix_scope
    ON retention_matrix(tenant_id, COALESCE(workspace_id, ''), data_domain, resource_type, policy_version)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS consent_ledger (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26),
    subject_id          VARCHAR(128) NOT NULL,
    email               VARCHAR(320),
    channel             VARCHAR(64) NOT NULL,
    purpose             VARCHAR(128) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    source              VARCHAR(128) NOT NULL DEFAULT 'UNKNOWN',
    evidence_ref        VARCHAR(255),
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata            JSONB NOT NULL DEFAULT '{}',
    previous_hash       VARCHAR(64),
    event_hash          VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_consent_subject
    ON consent_ledger(tenant_id, workspace_id, subject_id, occurred_at DESC)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_consent_hash
    ON consent_ledger(tenant_id, event_hash)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS privacy_requests (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26),
    subject_id          VARCHAR(128) NOT NULL,
    email               VARCHAR(320),
    request_type        VARCHAR(64) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    due_at              TIMESTAMPTZ,
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    assigned_to         VARCHAR(26),
    evidence            JSONB NOT NULL DEFAULT '{}',
    result_uri          VARCHAR(1000),
    notes               VARCHAR(2000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_privacy_requests_scope
    ON privacy_requests(tenant_id, workspace_id, status, due_at)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS compliance_export_jobs (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(26) NOT NULL,
    workspace_id        VARCHAR(26),
    export_type         VARCHAR(64) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    format              VARCHAR(16) NOT NULL DEFAULT 'JSON',
    requested_by        VARCHAR(26),
    filters             JSONB NOT NULL DEFAULT '{}',
    result_uri          VARCHAR(1000),
    row_count           BIGINT NOT NULL DEFAULT 0,
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_compliance_export_jobs_scope
    ON compliance_export_jobs(tenant_id, workspace_id, requested_at DESC)
    WHERE deleted_at IS NULL;
