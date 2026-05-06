-- V9: Add updated_at column to send_job_checkpoints table
-- Fixes: Schema-validation: missing column [updated_at] in table [send_job_checkpoints]

ALTER TABLE send_job_checkpoints ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
