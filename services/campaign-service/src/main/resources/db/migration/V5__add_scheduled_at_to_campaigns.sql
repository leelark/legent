-- V5: Add scheduled_at column to campaigns table
-- Fixes: Schema-validation: missing column [scheduled_at] in table [campaigns]

ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMPTZ;
