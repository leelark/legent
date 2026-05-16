-- V9: Sender-domain verification must prove tenant/workspace-owned DNS challenge tokens.

ALTER TABLE sender_domains
    ADD COLUMN IF NOT EXISTS verification_token_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS verification_record_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS verification_record_value VARCHAR(512),
    ADD COLUMN IF NOT EXISTS verification_token_issued_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS verification_token_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS ownership_token_verified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS ownership_token_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS verification_failure_reason VARCHAR(512);

UPDATE sender_domains
SET status = 'PENDING',
    is_active = false,
    ownership_token_verified = false
WHERE COALESCE(ownership_token_verified, false) = false;

CREATE TABLE IF NOT EXISTS sender_domain_verification_history (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    sender_domain_id VARCHAR(36) NOT NULL,
    domain_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    spf_verified BOOLEAN NOT NULL DEFAULT false,
    dkim_verified BOOLEAN NOT NULL DEFAULT false,
    dmarc_verified BOOLEAN NOT NULL DEFAULT false,
    ownership_token_verified BOOLEAN NOT NULL DEFAULT false,
    verification_record_name VARCHAR(255),
    failure_reason VARCHAR(512),
    verified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(26),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sender_domain_verification_history_scope
    ON sender_domain_verification_history (tenant_id, workspace_id, sender_domain_id, verified_at DESC);

CREATE INDEX IF NOT EXISTS idx_sender_domains_owned_verification
    ON sender_domains (tenant_id, workspace_id, ownership_token_verified, ownership_token_verified_at DESC);
