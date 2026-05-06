-- V4: Automation workspace strict ownership, lifecycle metadata, and idempotency ledger.

ALTER TABLE workflows
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE',
    ADD COLUMN IF NOT EXISTS active_definition_version INT,
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;

UPDATE workflows
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE workflows
    ALTER COLUMN workspace_id SET NOT NULL;

UPDATE workflows
SET active_definition_version = 1
WHERE active_definition_version IS NULL;

CREATE INDEX IF NOT EXISTS idx_workflows_tenant_workspace
    ON workflows (tenant_id, workspace_id)
    WHERE deleted_at IS NULL;

ALTER TABLE workflow_definitions
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS graph_version INT NOT NULL DEFAULT 2,
    ADD COLUMN IF NOT EXISTS published BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE workflow_definitions d
SET workspace_id = COALESCE(w.workspace_id, 'workspace-default')
FROM workflows w
WHERE d.workflow_id = w.id
  AND (d.workspace_id IS NULL OR d.workspace_id = '');

UPDATE workflow_definitions
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE workflow_definitions
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_workflow_definitions_scope
    ON workflow_definitions (tenant_id, workspace_id, workflow_id, version DESC);

ALTER TABLE workflow_instances
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS team_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE',
    ADD COLUMN IF NOT EXISTS environment_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS request_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(128);

UPDATE workflow_instances i
SET workspace_id = COALESCE(w.workspace_id, 'workspace-default')
FROM workflows w
WHERE i.workflow_id = w.id
  AND (i.workspace_id IS NULL OR i.workspace_id = '');

UPDATE workflow_instances
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE workflow_instances
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_instances_tenant_workspace_status
    ON workflow_instances (tenant_id, workspace_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_instances_tenant_workspace_workflow
    ON workflow_instances (tenant_id, workspace_id, workflow_id, created_at DESC);

ALTER TABLE instance_history
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS details JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE instance_history h
SET workspace_id = COALESCE(i.workspace_id, 'workspace-default')
FROM workflow_instances i
WHERE h.instance_id = i.id
  AND (h.workspace_id IS NULL OR h.workspace_id = '');

UPDATE instance_history
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE instance_history
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_instance_history_scope
    ON instance_history (tenant_id, workspace_id, instance_id, executed_at DESC);

CREATE TABLE IF NOT EXISTS automation_event_idempotency (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_id VARCHAR(128),
    idempotency_key VARCHAR(128),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_automation_event_idempotency_event
    ON automation_event_idempotency (tenant_id, workspace_id, event_type, event_id)
    WHERE event_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_automation_event_idempotency_key
    ON automation_event_idempotency (tenant_id, workspace_id, event_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_automation_event_idempotency_processed
    ON automation_event_idempotency (tenant_id, workspace_id, processed_at DESC);

CREATE TABLE IF NOT EXISTS workflow_schedules (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    workflow_id VARCHAR(64) NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    schedule_type VARCHAR(32) NOT NULL DEFAULT 'CRON',
    cron_expression VARCHAR(128) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    next_run_at TIMESTAMPTZ,
    last_run_at TIMESTAMPTZ,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_workflow_schedules_scope
    ON workflow_schedules (tenant_id, workspace_id, workflow_id, status);

CREATE TABLE IF NOT EXISTS workflow_workspace_migration_audit (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workflow_id VARCHAR(64) NOT NULL,
    old_workspace_id VARCHAR(64),
    new_workspace_id VARCHAR(64) NOT NULL,
    reason VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
