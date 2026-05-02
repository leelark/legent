-- V4: Add missing columns to campaign tables
-- Fixes schema validation errors for missing columns

ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMPTZ;
ALTER TABLE campaign_approvals ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
