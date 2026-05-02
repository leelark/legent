-- V4: Add version column to consent_records table
-- Fixes: Schema-validation: missing column [version] in table [consent_records]

ALTER TABLE consent_records ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
