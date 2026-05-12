-- V14: Phase 5 global enterprise operating model primitives.

CREATE TABLE IF NOT EXISTS global_operating_models (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    model_key               VARCHAR(128) NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    topology_mode           VARCHAR(32) NOT NULL DEFAULT 'ACTIVE_WARM',
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    primary_region          VARCHAR(64) NOT NULL,
    standby_regions         JSONB NOT NULL DEFAULT '[]',
    active_regions          JSONB NOT NULL DEFAULT '[]',
    rpo_target_minutes      INT NOT NULL DEFAULT 15,
    rto_target_minutes      INT NOT NULL DEFAULT 60,
    traffic_policy          JSONB NOT NULL DEFAULT '{}',
    promotion_state         VARCHAR(32) NOT NULL DEFAULT 'PRIMARY_HEALTHY',
    failover_state          VARCHAR(32) NOT NULL DEFAULT 'LOCKED',
    last_drill_at           TIMESTAMPTZ,
    metadata                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_global_operating_model_key
    ON global_operating_models(tenant_id, COALESCE(workspace_id, ''), model_key)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_global_operating_model_status
    ON global_operating_models(tenant_id, workspace_id, topology_mode, status)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS global_failover_drills (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    operating_model_id      VARCHAR(26) REFERENCES global_operating_models(id) ON DELETE SET NULL,
    drill_type              VARCHAR(64) NOT NULL DEFAULT 'PLANNED',
    source_region           VARCHAR(64),
    target_region           VARCHAR(64),
    affected_services       JSONB NOT NULL DEFAULT '[]',
    planned_rpo_minutes     INT NOT NULL DEFAULT 15,
    planned_rto_minutes     INT NOT NULL DEFAULT 60,
    actual_rpo_minutes      INT NOT NULL DEFAULT 0,
    actual_rto_minutes      INT NOT NULL DEFAULT 0,
    verdict                 VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    findings                JSONB NOT NULL DEFAULT '[]',
    evidence                JSONB NOT NULL DEFAULT '{}',
    started_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_global_failover_drills_scope
    ON global_failover_drills(tenant_id, workspace_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS tenant_data_residency_policies (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    policy_key              VARCHAR(128) NOT NULL,
    data_class              VARCHAR(128) NOT NULL,
    home_region             VARCHAR(64) NOT NULL,
    allowed_regions         JSONB NOT NULL DEFAULT '[]',
    blocked_regions         JSONB NOT NULL DEFAULT '[]',
    failover_allowed        BOOLEAN NOT NULL DEFAULT FALSE,
    legal_basis             VARCHAR(128) NOT NULL DEFAULT 'CONTRACT',
    enforcement_mode        VARCHAR(32) NOT NULL DEFAULT 'ENFORCE',
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    metadata                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_data_residency_policy_key
    ON tenant_data_residency_policies(tenant_id, COALESCE(workspace_id, ''), policy_key)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_tenant_data_residency_class
    ON tenant_data_residency_policies(tenant_id, workspace_id, data_class, status)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS tenant_encryption_policies (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    policy_key              VARCHAR(128) NOT NULL,
    data_class              VARCHAR(128) NOT NULL,
    key_provider            VARCHAR(128) NOT NULL,
    key_ref                 VARCHAR(1000) NOT NULL,
    algorithm               VARCHAR(64) NOT NULL DEFAULT 'AES-256-GCM',
    rotation_days           INT NOT NULL DEFAULT 90,
    residency_policy_id     VARCHAR(26) REFERENCES tenant_data_residency_policies(id) ON DELETE SET NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_rotated_at         TIMESTAMPTZ,
    next_rotation_at        TIMESTAMPTZ,
    metadata                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_encryption_policy_key
    ON tenant_encryption_policies(tenant_id, COALESCE(workspace_id, ''), policy_key)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS governance_legal_holds (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    hold_key                VARCHAR(128) NOT NULL,
    subject_type            VARCHAR(128) NOT NULL,
    subject_id              VARCHAR(128) NOT NULL,
    data_domains            JSONB NOT NULL DEFAULT '[]',
    reason                  TEXT NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    applied_by              VARCHAR(26),
    applied_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    released_by             VARCHAR(26),
    released_at             TIMESTAMPTZ,
    release_reason          TEXT,
    evidence                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_governance_legal_hold_key
    ON governance_legal_holds(tenant_id, COALESCE(workspace_id, ''), hold_key)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_governance_legal_hold_subject
    ON governance_legal_holds(tenant_id, workspace_id, subject_type, subject_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS governance_data_lineage_edges (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    source_type             VARCHAR(128) NOT NULL,
    source_id               VARCHAR(128) NOT NULL,
    target_type             VARCHAR(128) NOT NULL,
    target_id               VARCHAR(128) NOT NULL,
    data_class              VARCHAR(128) NOT NULL,
    transform_type          VARCHAR(128) NOT NULL DEFAULT 'COPY',
    purpose                 VARCHAR(255),
    policy_refs             JSONB NOT NULL DEFAULT '[]',
    confidence              NUMERIC(6,4) NOT NULL DEFAULT 1,
    metadata                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_governance_lineage_source
    ON governance_data_lineage_edges(tenant_id, workspace_id, source_type, source_id)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_governance_lineage_target
    ON governance_data_lineage_edges(tenant_id, workspace_id, target_type, target_id)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS governance_policy_simulation_runs (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    simulation_key          VARCHAR(128) NOT NULL,
    policy_type             VARCHAR(128) NOT NULL,
    artifact_type           VARCHAR(128) NOT NULL,
    artifact_id             VARCHAR(128),
    input_context           JSONB NOT NULL DEFAULT '{}',
    result                  JSONB NOT NULL DEFAULT '{}',
    verdict                 VARCHAR(32) NOT NULL DEFAULT 'REVIEW',
    findings                JSONB NOT NULL DEFAULT '[]',
    simulated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_governance_policy_simulation_scope
    ON governance_policy_simulation_runs(tenant_id, workspace_id, policy_type, simulated_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS governance_evidence_packs (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    pack_key                VARCHAR(128) NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'READY',
    scope                   JSONB NOT NULL DEFAULT '{}',
    evidence_refs           JSONB NOT NULL DEFAULT '[]',
    generated_uri           VARCHAR(1000),
    generated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_governance_evidence_pack_key
    ON governance_evidence_packs(tenant_id, COALESCE(workspace_id, ''), pack_key)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS marketplace_connector_templates (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    connector_key           VARCHAR(128) NOT NULL,
    category                VARCHAR(64) NOT NULL,
    display_name            VARCHAR(255) NOT NULL,
    vendor                  VARCHAR(255) NOT NULL,
    auth_modes              JSONB NOT NULL DEFAULT '[]',
    supported_events        JSONB NOT NULL DEFAULT '[]',
    capabilities            JSONB NOT NULL DEFAULT '{}',
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    metadata                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_marketplace_connector_template_key
    ON marketplace_connector_templates(tenant_id, connector_key)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS marketplace_connector_instances (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    template_id             VARCHAR(26) REFERENCES marketplace_connector_templates(id) ON DELETE SET NULL,
    instance_key            VARCHAR(128) NOT NULL,
    connector_key           VARCHAR(128) NOT NULL,
    display_name            VARCHAR(255) NOT NULL,
    category                VARCHAR(64) NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    auth_mode               VARCHAR(64) NOT NULL,
    credential_ref          VARCHAR(1000),
    config                  JSONB NOT NULL DEFAULT '{}',
    last_validated_at       TIMESTAMPTZ,
    validation_result       JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_marketplace_connector_instance_key
    ON marketplace_connector_instances(tenant_id, COALESCE(workspace_id, ''), instance_key)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS marketplace_sync_jobs (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    connector_instance_id   VARCHAR(26) REFERENCES marketplace_connector_instances(id) ON DELETE SET NULL,
    sync_type               VARCHAR(128) NOT NULL,
    direction               VARCHAR(32) NOT NULL DEFAULT 'IMPORT',
    dry_run                 BOOLEAN NOT NULL DEFAULT TRUE,
    status                  VARCHAR(32) NOT NULL DEFAULT 'DRY_RUN_COMPLETE',
    request                 JSONB NOT NULL DEFAULT '{}',
    result                  JSONB NOT NULL DEFAULT '{}',
    estimated_records       BIGINT NOT NULL DEFAULT 0,
    started_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_marketplace_sync_jobs_scope
    ON marketplace_sync_jobs(tenant_id, workspace_id, connector_instance_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS autonomous_optimization_policies (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    policy_key              VARCHAR(128) NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    mode                    VARCHAR(64) NOT NULL DEFAULT 'SUGGEST_ONLY',
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    target_scope            JSONB NOT NULL DEFAULT '{}',
    constraints             JSONB NOT NULL DEFAULT '{}',
    guardrails              JSONB NOT NULL DEFAULT '{}',
    rollback_policy         JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_autonomous_optimization_policy_key
    ON autonomous_optimization_policies(tenant_id, COALESCE(workspace_id, ''), policy_key)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS autonomous_optimization_recommendations (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    policy_id               VARCHAR(26) REFERENCES autonomous_optimization_policies(id) ON DELETE SET NULL,
    artifact_type           VARCHAR(128) NOT NULL,
    artifact_id             VARCHAR(128) NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
    risk_score              INT NOT NULL DEFAULT 0,
    input_signals           JSONB NOT NULL DEFAULT '{}',
    explanation             JSONB NOT NULL DEFAULT '{}',
    recommendation          JSONB NOT NULL DEFAULT '{}',
    brand_result            JSONB NOT NULL DEFAULT '{}',
    compliance_result       JSONB NOT NULL DEFAULT '{}',
    target_snapshot         JSONB NOT NULL DEFAULT '{}',
    applied_snapshot        JSONB NOT NULL DEFAULT '{}',
    rollback_snapshot       JSONB NOT NULL DEFAULT '{}',
    decision_by             VARCHAR(26),
    decision_at             TIMESTAMPTZ,
    decision_note           TEXT,
    applied_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_autonomous_optimization_recommendations
    ON autonomous_optimization_recommendations(tenant_id, workspace_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS autonomous_optimization_rollbacks (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    recommendation_id       VARCHAR(26) REFERENCES autonomous_optimization_recommendations(id) ON DELETE SET NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    reason                  TEXT NOT NULL,
    rollback_snapshot       JSONB NOT NULL DEFAULT '{}',
    evidence                JSONB NOT NULL DEFAULT '{}',
    rolled_back_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
