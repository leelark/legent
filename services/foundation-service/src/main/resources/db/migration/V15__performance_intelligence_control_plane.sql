-- V15: Performance Intelligence control-plane ledgers.

CREATE TABLE IF NOT EXISTS personalization_evaluation_runs (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    region                  VARCHAR(64),
    evaluation_key          VARCHAR(128) NOT NULL,
    subject_id              VARCHAR(128),
    event_type              VARCHAR(128) NOT NULL,
    input_profile           JSONB NOT NULL DEFAULT '{}',
    event_payload           JSONB NOT NULL DEFAULT '{}',
    segment_hits            JSONB NOT NULL DEFAULT '[]',
    variant_decision        JSONB NOT NULL DEFAULT '{}',
    personalization         JSONB NOT NULL DEFAULT '{}',
    guardrail_findings      JSONB NOT NULL DEFAULT '[]',
    latency_ms              INT NOT NULL DEFAULT 0,
    slo_pass                BOOLEAN NOT NULL DEFAULT TRUE,
    metadata                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_personalization_runs_scope
    ON personalization_evaluation_runs(tenant_id, workspace_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_personalization_runs_subject
    ON personalization_evaluation_runs(tenant_id, workspace_id, subject_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS performance_optimization_policies (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    policy_key              VARCHAR(128) NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    optimization_type       VARCHAR(64) NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    objective               TEXT,
    target_metric           VARCHAR(128),
    guardrails              JSONB NOT NULL DEFAULT '{}',
    rollback_policy         JSONB NOT NULL DEFAULT '{}',
    approval_policy         JSONB NOT NULL DEFAULT '{}',
    metadata                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_performance_optimization_policy_key
    ON performance_optimization_policies(tenant_id, COALESCE(workspace_id, ''), policy_key)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_performance_optimization_policy_type
    ON performance_optimization_policies(tenant_id, workspace_id, optimization_type, status)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS performance_optimization_runs (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    policy_id               VARCHAR(26) REFERENCES performance_optimization_policies(id) ON DELETE SET NULL,
    optimization_type       VARCHAR(64) NOT NULL,
    artifact_type           VARCHAR(128),
    artifact_id             VARCHAR(128),
    signals                 JSONB NOT NULL DEFAULT '{}',
    score                   INT NOT NULL DEFAULT 0,
    risk_band               VARCHAR(32) NOT NULL DEFAULT 'LOW',
    recommendations         JSONB NOT NULL DEFAULT '[]',
    approval_required       BOOLEAN NOT NULL DEFAULT FALSE,
    rollback_required       BOOLEAN NOT NULL DEFAULT FALSE,
    blocked_reasons         JSONB NOT NULL DEFAULT '[]',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_performance_optimization_runs
    ON performance_optimization_runs(tenant_id, workspace_id, optimization_type, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS extension_governance_packages (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    package_key             VARCHAR(128) NOT NULL,
    display_name            VARCHAR(255) NOT NULL,
    package_type            VARCHAR(64) NOT NULL DEFAULT 'HYBRID',
    status                  VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    scopes                  JSONB NOT NULL DEFAULT '[]',
    manifest                JSONB NOT NULL DEFAULT '{}',
    scripts                 JSONB NOT NULL DEFAULT '[]',
    test_requirements       JSONB NOT NULL DEFAULT '[]',
    approval_status         VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    metadata                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_extension_governance_package_key
    ON extension_governance_packages(tenant_id, COALESCE(workspace_id, ''), package_key)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS extension_governance_test_runs (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    package_id              VARCHAR(26) NOT NULL REFERENCES extension_governance_packages(id) ON DELETE CASCADE,
    status                  VARCHAR(32) NOT NULL,
    findings                JSONB NOT NULL DEFAULT '[]',
    forbidden_tokens        JSONB NOT NULL DEFAULT '[]',
    passed_checks           JSONB NOT NULL DEFAULT '[]',
    missing_checks          JSONB NOT NULL DEFAULT '[]',
    evidence                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_extension_governance_test_runs
    ON extension_governance_test_runs(tenant_id, workspace_id, package_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS operations_assistance_reviews (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    workspace_id            VARCHAR(64),
    operation_type          VARCHAR(64) NOT NULL,
    artifact_type           VARCHAR(128),
    artifact_id             VARCHAR(128),
    severity                VARCHAR(16) NOT NULL DEFAULT 'P3',
    status                  VARCHAR(32) NOT NULL DEFAULT 'READY',
    risk_score              INT NOT NULL DEFAULT 0,
    telemetry               JSONB NOT NULL DEFAULT '{}',
    checklist               JSONB NOT NULL DEFAULT '[]',
    recommended_actions     JSONB NOT NULL DEFAULT '[]',
    evidence_refs           JSONB NOT NULL DEFAULT '[]',
    approval_required       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_operations_assistance_reviews
    ON operations_assistance_reviews(tenant_id, workspace_id, operation_type, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS workflow_benchmark_runs (
    id                                  VARCHAR(26) PRIMARY KEY,
    tenant_id                           VARCHAR(64) NOT NULL,
    workspace_id                        VARCHAR(64),
    benchmark_key                       VARCHAR(128) NOT NULL,
    flow_name                           VARCHAR(255) NOT NULL,
    competitor                          VARCHAR(128) NOT NULL DEFAULT 'Salesforce MCE',
    campaign_creation_seconds           INT NOT NULL DEFAULT 0,
    launch_errors                       INT NOT NULL DEFAULT 0,
    observability_score                 INT NOT NULL DEFAULT 0,
    competitor_creation_seconds         INT NOT NULL DEFAULT 0,
    competitor_launch_errors            INT NOT NULL DEFAULT 0,
    competitor_observability_score      INT NOT NULL DEFAULT 0,
    verdict                             VARCHAR(32) NOT NULL DEFAULT 'WATCH',
    deltas                              JSONB NOT NULL DEFAULT '{}',
    evidence                            JSONB NOT NULL DEFAULT '{}',
    created_at                          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                          VARCHAR(26),
    deleted_at                          TIMESTAMPTZ,
    version                             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_workflow_benchmark_runs
    ON workflow_benchmark_runs(tenant_id, workspace_id, benchmark_key, created_at DESC)
    WHERE deleted_at IS NULL;
