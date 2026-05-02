-- V8: Add created_by column to tables missing it
-- Fixes schema validation errors

ALTER TABLE data_extension_records ADD COLUMN IF NOT EXISTS created_by VARCHAR(26);
ALTER TABLE data_extension_fields ADD COLUMN IF NOT EXISTS created_by VARCHAR(26);
ALTER TABLE consent_records ADD COLUMN IF NOT EXISTS created_by VARCHAR(26);
ALTER TABLE consent_audit_log ADD COLUMN IF NOT EXISTS created_by VARCHAR(26);
ALTER TABLE subscriber_merge_history ADD COLUMN IF NOT EXISTS created_by VARCHAR(26);
ALTER TABLE segment_memberships ADD COLUMN IF NOT EXISTS created_by VARCHAR(26);
