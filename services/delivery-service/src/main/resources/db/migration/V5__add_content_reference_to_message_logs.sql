-- V5: Add content_reference column to message_logs table
-- Required by MessageLog entity

ALTER TABLE message_logs ADD COLUMN IF NOT EXISTS content_reference VARCHAR(255);
