-- V5: Add missing deleted_at and version columns to sender_domains
-- Fixes: Schema-validation: missing column [deleted_at] in table [sender_domains]
-- The SenderDomain entity extends BaseEntity requiring deleted_at and version

ALTER TABLE sender_domains 
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
