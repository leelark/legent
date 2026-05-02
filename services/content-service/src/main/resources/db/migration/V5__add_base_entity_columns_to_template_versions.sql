-- V5: Add missing BaseEntity columns to template_versions
-- Fixes: Schema-validation: missing columns in template_versions table

ALTER TABLE template_versions 
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
