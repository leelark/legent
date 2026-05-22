-- Scope campaign checkpoint and recovery tables by workspace.
-- Send jobs and batches gained workspace ownership in V11; this aligns the
-- checkpoint/resume compatibility tables with those ownership boundaries.

ALTER TABLE send_job_checkpoints
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64);

UPDATE send_job_checkpoints c
SET workspace_id = COALESCE(j.workspace_id, 'workspace-default')
FROM send_jobs j
WHERE c.job_id = j.id
  AND (c.workspace_id IS NULL OR c.workspace_id = '');

UPDATE send_job_checkpoints
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE send_job_checkpoints
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_send_job_checkpoints_scope_job
    ON send_job_checkpoints (tenant_id, workspace_id, job_id, sequence_number DESC)
    WHERE deleted_at IS NULL;

ALTER TABLE send_job_resume_state
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64);

UPDATE send_job_resume_state r
SET workspace_id = COALESCE(j.workspace_id, 'workspace-default')
FROM send_jobs j
WHERE r.job_id = j.id
  AND (r.workspace_id IS NULL OR r.workspace_id = '');

UPDATE send_job_resume_state
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE send_job_resume_state
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_resume_state_scope_pending
    ON send_job_resume_state (tenant_id, workspace_id, job_id)
    WHERE completed_at IS NULL;

ALTER TABLE batch_recovery_tracking
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64);

UPDATE batch_recovery_tracking r
SET workspace_id = COALESCE(j.workspace_id, 'workspace-default')
FROM send_jobs j
WHERE r.job_id = j.id
  AND (r.workspace_id IS NULL OR r.workspace_id = '');

UPDATE batch_recovery_tracking
SET workspace_id = 'workspace-default'
WHERE workspace_id IS NULL OR workspace_id = '';

ALTER TABLE batch_recovery_tracking
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_batch_recovery_scope_job
    ON batch_recovery_tracking (tenant_id, workspace_id, job_id, status);
