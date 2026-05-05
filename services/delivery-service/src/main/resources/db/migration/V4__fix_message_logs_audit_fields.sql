-- V4 Fix: Add missing audit fields to message_logs table
-- Required by BaseEntity (created_by, deleted_at, version) and MessageLog entity (content_reference)

ALTER TABLE message_logs
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(36),
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS content_reference VARCHAR(255);

-- Add indexes for soft delete queries
CREATE INDEX IF NOT EXISTS idx_message_logs_deleted_at ON message_logs(deleted_at) WHERE deleted_at IS NULL;
