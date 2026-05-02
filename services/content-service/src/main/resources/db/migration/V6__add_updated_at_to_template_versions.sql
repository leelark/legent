-- V6: Add missing updated_at column to template_versions
-- Fixes: Schema-validation: missing column [updated_at] in table [template_versions]

ALTER TABLE template_versions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
