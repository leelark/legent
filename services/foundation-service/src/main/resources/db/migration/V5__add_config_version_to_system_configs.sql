-- V5: Add missing config_version column to system_configs
-- Fixes: Schema-validation: missing column [config_version] in table [system_configs]

ALTER TABLE system_configs ADD COLUMN IF NOT EXISTS config_version INT NOT NULL DEFAULT 1;
