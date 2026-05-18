CREATE TABLE IF NOT EXISTS send_batch_recipients (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    job_id VARCHAR(64) NOT NULL,
    batch_id VARCHAR(64) NOT NULL REFERENCES send_batches(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    subscriber_id VARCHAR(64),
    email VARCHAR(320) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    processed_at TIMESTAMPTZ,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_send_batch_recipients_sequence
    ON send_batch_recipients (tenant_id, workspace_id, job_id, batch_id, sequence_number)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_send_batch_recipients_retry_page
    ON send_batch_recipients (tenant_id, workspace_id, job_id, batch_id, status, sequence_number)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_send_batch_recipients_subscriber
    ON send_batch_recipients (tenant_id, workspace_id, job_id, batch_id, subscriber_id)
    WHERE deleted_at IS NULL AND subscriber_id IS NOT NULL;
