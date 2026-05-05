-- V12: Add updated_at column to suppressions table
-- Fixes schema validation error for BaseEntity

ALTER TABLE suppressions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
