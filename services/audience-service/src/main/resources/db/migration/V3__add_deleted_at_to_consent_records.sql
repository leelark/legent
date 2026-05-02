-- V3: Add all missing BaseEntity columns to consent_records table
-- Fixes: Schema-validation: missing columns in table [consent_records]

ALTER TABLE consent_records 
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
