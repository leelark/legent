-- =============================================
-- Campaign Service Approval and Checkpointing Schema
-- Version: V2
-- Adds approval workflow and send checkpointing/resume support
-- =============================================

-- ── Campaign Approval Workflow ──
CREATE TABLE IF NOT EXISTS campaign_approvals (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    campaign_id         VARCHAR(36) NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    requested_by        VARCHAR(36) NOT NULL,
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by         VARCHAR(36),
    approved_at         TIMESTAMPTZ,
    rejection_reason    VARCHAR(1000),
    comments            TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_campaign_approval UNIQUE (campaign_id, status)
);

CREATE INDEX idx_campaign_approvals_tenant ON campaign_approvals(tenant_id);
CREATE INDEX idx_campaign_approvals_status ON campaign_approvals(tenant_id, status);

-- ── Send Job Checkpoints ──
CREATE TABLE IF NOT EXISTS send_job_checkpoints (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    job_id              VARCHAR(36) NOT NULL REFERENCES send_jobs(id) ON DELETE CASCADE,
    checkpoint_type     VARCHAR(20) NOT NULL,
    sequence_number     INT NOT NULL,
    last_processed_id   VARCHAR(36),
    processed_count     BIGINT NOT NULL DEFAULT 0,
    metadata            JSONB DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_job_checkpoint UNIQUE (job_id, checkpoint_type, sequence_number)
);

CREATE INDEX idx_checkpoints_job ON send_job_checkpoints(job_id);
CREATE INDEX idx_checkpoints_type ON send_job_checkpoints(job_id, checkpoint_type);

-- ── Send Job Resume State ──
CREATE TABLE IF NOT EXISTS send_job_resume_state (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    job_id              VARCHAR(36) NOT NULL REFERENCES send_jobs(id) ON DELETE CASCADE,
    resume_from_checkpoint_id VARCHAR(36) REFERENCES send_job_checkpoints(id),
    failed_batches      JSONB DEFAULT '[]',
    pending_batches     JSONB DEFAULT '[]',
    retry_count         INT NOT NULL DEFAULT 0,
    last_error          TEXT,
    resumed_at          TIMESTAMPTZ,
    resumed_by          VARCHAR(36),
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_resume_state_job ON send_job_resume_state(job_id);
CREATE INDEX idx_resume_state_pending ON send_job_resume_state(tenant_id) WHERE completed_at IS NULL;

-- ── Batch Recovery Tracking ──
CREATE TABLE IF NOT EXISTS batch_recovery_tracking (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    job_id              VARCHAR(36) NOT NULL REFERENCES send_jobs(id) ON DELETE CASCADE,
    batch_id            VARCHAR(36) NOT NULL,
    recovery_attempt    INT NOT NULL DEFAULT 1,
    previous_error      TEXT,
    recovery_strategy   VARCHAR(50) NOT NULL DEFAULT 'RETRY',
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    recovered_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_batch_recovery_job ON batch_recovery_tracking(job_id);
CREATE INDEX idx_batch_recovery_pending ON batch_recovery_tracking(job_id, status) WHERE status = 'PENDING';

-- ── Add approval fields to campaigns ──
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS approval_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS approved_by VARCHAR(36);
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS current_approver VARCHAR(36);

-- ── Add checkpoint and resume fields to send_jobs ──
ALTER TABLE send_jobs ADD COLUMN IF NOT EXISTS last_checkpoint_at TIMESTAMPTZ;
ALTER TABLE send_jobs ADD COLUMN IF NOT EXISTS checkpoint_interval INT DEFAULT 1000;
ALTER TABLE send_jobs ADD COLUMN IF NOT EXISTS can_resume BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE send_jobs ADD COLUMN IF NOT EXISTS resumed_from_job_id VARCHAR(36);

-- ── Add status for approval workflow ──
-- Note: Status values need to be handled in application code
-- PENDING_APPROVAL, APPROVED, REJECTED
