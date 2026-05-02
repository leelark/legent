-- V6: Add all missing BaseEntity columns to audience tables
-- Fixes schema validation errors for multiple tables

-- data_extension_fields
ALTER TABLE data_extension_fields 
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- consent_records  
ALTER TABLE consent_records
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- double_optin_tokens
ALTER TABLE double_optin_tokens
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(36);

-- consent_audit_log
ALTER TABLE consent_audit_log
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- subscriber_merge_history
ALTER TABLE subscriber_merge_history
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- segment_memberships
ALTER TABLE segment_memberships
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(36);

-- suppressions (add version)
ALTER TABLE suppressions ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- import_jobs (add deleted_at)
ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
