-- V6: Add created_at column to subscriber_summaries table
-- Fixes: Schema-validation: missing column [created_at] in table [subscriber_summaries]

ALTER TABLE subscriber_summaries ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
