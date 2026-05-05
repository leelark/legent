-- V11: Add created_at column to segment_memberships table
-- Fixes schema validation error for BaseEntity

ALTER TABLE segment_memberships ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
