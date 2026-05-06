-- V10: Add all remaining BaseEntity columns to send_job_checkpoints table
-- Fixes all remaining schema validation errors

ALTER TABLE send_job_checkpoints 
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
