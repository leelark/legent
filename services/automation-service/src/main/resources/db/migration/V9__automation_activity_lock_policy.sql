-- V9: Live Automation Studio activity lock policy.

CREATE TABLE IF NOT EXISTS automation_activity_locks (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    workspace_id        VARCHAR(64) NOT NULL,
    activity_id         VARCHAR(26) NOT NULL REFERENCES automation_activities(id) ON DELETE CASCADE,
    run_id              VARCHAR(26) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    locked_until        TIMESTAMPTZ NOT NULL,
    lock_owner          VARCHAR(64) NOT NULL DEFAULT 'RUN',
    override_reason     TEXT,
    acquired_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    heartbeat_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    released_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(26),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_automation_activity_locks_active
    ON automation_activity_locks(tenant_id, workspace_id, activity_id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_automation_activity_locks_scope
    ON automation_activity_locks(tenant_id, workspace_id, activity_id, locked_until DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_automation_activity_locks_run
    ON automation_activity_locks(tenant_id, workspace_id, activity_id, run_id)
    WHERE deleted_at IS NULL;
