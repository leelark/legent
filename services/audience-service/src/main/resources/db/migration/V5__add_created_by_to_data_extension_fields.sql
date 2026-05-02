-- V5: Add created_by column to data_extension_fields table
-- Fixes: Schema-validation: missing column [created_by] in table [data_extension_fields]

ALTER TABLE data_extension_fields ADD COLUMN IF NOT EXISTS created_by VARCHAR(26);
