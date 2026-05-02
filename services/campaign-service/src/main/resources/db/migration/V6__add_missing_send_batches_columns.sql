-- V6: Add missing columns to send_batches table
-- Fixes: Schema-validation: missing column [retry_count] in table [send_batches]

ALTER TABLE send_batches ADD COLUMN IF NOT EXISTS retry_count INT DEFAULT 0;
