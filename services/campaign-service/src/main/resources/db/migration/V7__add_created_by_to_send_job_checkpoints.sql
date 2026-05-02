-- V7: Add missing BaseEntity columns to send_job_checkpoints table
-- Fixes: Schema-validation: missing columns in table [send_job_checkpoints]

ALTER TABLE send_job_checkpoints 
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(26),
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
