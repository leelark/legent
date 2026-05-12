-- V13: Phase 4 differentiation platform primitives.

CREATE TABLE IF NOT EXISTS ai_copilot_recommendations (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(26) NOT NULL,
    workspace_id            VARCHAR(26),
    artifact_type           VARCHAR(64) NOT NULL,
    artifact_id             VARCHAR(128),
    objective               VARCHAR(1000) NOT NULL,
    audience_summary        TEXT,
    risk_score              INT NOT NULL DEFAULT 0,
    approval_required       BOOLEAN NOT NULL DEFAULT TRUE,
    status                  VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
    policy_context          JSONB NOT NULL DEFAULT '{}',
    candidate_content       JSONB NOT NULL DEFAULT '{}',
    constraints             JSONB NOT NULL DEFAULT '[]',
    recommendations         JSONB NOT NULL DEFAULT '{}',
    approved_by             VARCHAR(26),
    approved_at             TIMESTAMPTZ,
    human_decision_note     TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_ai_copilot_scope
    ON ai_copilot_recommendations(tenant_id, workspace_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS realtime_decision_policies (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(26) NOT NULL,
    workspace_id            VARCHAR(26),
    policy_key              VARCHAR(128) NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    trigger_event           VARCHAR(128) NOT NULL DEFAULT 'PROFILE_UPDATED',
    channel                 VARCHAR(64) NOT NULL DEFAULT 'ANY',
    rules                   JSONB NOT NULL DEFAULT '{}',
    variants                JSONB NOT NULL DEFAULT '[]',
    guardrails              JSONB NOT NULL DEFAULT '{}',
    metadata                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_realtime_decision_policy_key
    ON realtime_decision_policies(tenant_id, COALESCE(workspace_id, ''), policy_key)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_realtime_decision_policy_status
    ON realtime_decision_policies(tenant_id, workspace_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS realtime_decision_events (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(26) NOT NULL,
    workspace_id            VARCHAR(26),
    policy_id               VARCHAR(26) NOT NULL REFERENCES realtime_decision_policies(id) ON DELETE CASCADE,
    subject_id              VARCHAR(128),
    event_type              VARCHAR(128) NOT NULL,
    input_profile           JSONB NOT NULL DEFAULT '{}',
    decision                JSONB NOT NULL DEFAULT '{}',
    confidence              NUMERIC(6,4) NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_realtime_decision_events_subject
    ON realtime_decision_events(tenant_id, workspace_id, subject_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS omnichannel_orchestration_flows (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(26) NOT NULL,
    workspace_id            VARCHAR(26),
    flow_key                VARCHAR(128) NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    transactional           BOOLEAN NOT NULL DEFAULT FALSE,
    channels                JSONB NOT NULL DEFAULT '[]',
    routing_rules           JSONB NOT NULL DEFAULT '{}',
    guardrails              JSONB NOT NULL DEFAULT '{}',
    metadata                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_omnichannel_flow_key
    ON omnichannel_orchestration_flows(tenant_id, COALESCE(workspace_id, ''), flow_key)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS omnichannel_simulation_runs (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(26) NOT NULL,
    workspace_id            VARCHAR(26),
    flow_id                 VARCHAR(26) NOT NULL REFERENCES omnichannel_orchestration_flows(id) ON DELETE CASCADE,
    recipient               JSONB NOT NULL DEFAULT '{}',
    event_payload           JSONB NOT NULL DEFAULT '{}',
    route                   JSONB NOT NULL DEFAULT '[]',
    result                  JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_omnichannel_simulation_runs
    ON omnichannel_simulation_runs(tenant_id, workspace_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS developer_app_packages (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(26) NOT NULL,
    workspace_id            VARCHAR(26),
    app_key                 VARCHAR(128) NOT NULL,
    display_name            VARCHAR(255) NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    api_version             VARCHAR(32) NOT NULL DEFAULT 'v1',
    scopes                  JSONB NOT NULL DEFAULT '[]',
    sdk_targets             JSONB NOT NULL DEFAULT '[]',
    sandbox_enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    marketplace_status      VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    webhook_replay_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    metadata                JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_developer_app_package_key
    ON developer_app_packages(tenant_id, app_key)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS developer_sandboxes (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(26) NOT NULL,
    workspace_id            VARCHAR(26),
    app_package_id          VARCHAR(26) NOT NULL REFERENCES developer_app_packages(id) ON DELETE CASCADE,
    status                  VARCHAR(32) NOT NULL DEFAULT 'READY',
    data_profile            VARCHAR(64) NOT NULL DEFAULT 'SMALL',
    seed_options            JSONB NOT NULL DEFAULT '{}',
    api_base_url            VARCHAR(1000),
    expires_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_developer_sandboxes_package
    ON developer_sandboxes(tenant_id, app_package_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS webhook_replay_jobs (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(26) NOT NULL,
    workspace_id            VARCHAR(26),
    app_package_id          VARCHAR(26) NOT NULL REFERENCES developer_app_packages(id) ON DELETE CASCADE,
    source_webhook_id       VARCHAR(128),
    target_url              VARCHAR(1000),
    event_types             JSONB NOT NULL DEFAULT '[]',
    from_time               TIMESTAMPTZ,
    to_time                 TIMESTAMPTZ,
    dry_run                 BOOLEAN NOT NULL DEFAULT TRUE,
    status                  VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    estimated_events        BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_webhook_replay_jobs_package
    ON webhook_replay_jobs(tenant_id, app_package_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS slo_operations_policies (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(26) NOT NULL,
    workspace_id            VARCHAR(26),
    service_name            VARCHAR(128) NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    slo_target_percent      NUMERIC(6,3) NOT NULL DEFAULT 99.900,
    window                  VARCHAR(32) NOT NULL DEFAULT '30d',
    error_budget_minutes    NUMERIC(12,3) NOT NULL DEFAULT 43.200,
    synthetic_probe         JSONB NOT NULL DEFAULT '{}',
    self_healing_actions    JSONB NOT NULL DEFAULT '[]',
    capacity_forecast       JSONB NOT NULL DEFAULT '{}',
    incident_automation     JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_slo_operations_policy_service
    ON slo_operations_policies(tenant_id, COALESCE(workspace_id, ''), service_name)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS slo_incident_automation_events (
    id                      VARCHAR(26) PRIMARY KEY,
    tenant_id               VARCHAR(26) NOT NULL,
    workspace_id            VARCHAR(26),
    slo_policy_id           VARCHAR(26) NOT NULL REFERENCES slo_operations_policies(id) ON DELETE CASCADE,
    service_name            VARCHAR(128) NOT NULL,
    severity                VARCHAR(16) NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    telemetry               JSONB NOT NULL DEFAULT '{}',
    automation_result       JSONB NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(26),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_slo_incident_events_scope
    ON slo_incident_automation_events(tenant_id, workspace_id, service_name, created_at DESC)
    WHERE deleted_at IS NULL;
