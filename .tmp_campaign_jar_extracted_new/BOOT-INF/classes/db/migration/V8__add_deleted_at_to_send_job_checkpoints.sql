-- V8: Add all missing BaseEntity columns to send_job_checkpoints table
-- Fixes: Schema-validation: missing columns in table [send_job_checkpoints]

ALTER TABLE send_job_checkpoints 
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
