-- V3: Add missing content_id column to campaigns table
-- Fixes: Schema-validation: missing column [content_id] in table [campaigns]

ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS content_id VARCHAR(36);
