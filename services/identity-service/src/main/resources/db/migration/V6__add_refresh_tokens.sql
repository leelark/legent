-- Fix 33: Create refresh_tokens table for secure token refresh mechanism
-- Refresh tokens are long-lived tokens used to obtain new access tokens

CREATE TABLE IF NOT EXISTS refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,  -- SHA-256 hash of the token
    user_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    user_agent TEXT,
    ip_address VARCHAR(45)
);

-- Index for faster lookups by user/tenant
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_tenant
    ON refresh_tokens (user_id, tenant_id);

-- Index for cleanup of expired tokens (partial indexes with CURRENT_TIMESTAMP require immutable functions)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);

-- Index for finding revoked tokens
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_revoked
    ON refresh_tokens (is_revoked)
    WHERE is_revoked = TRUE;

-- Add comment for documentation
COMMENT ON TABLE refresh_tokens IS 'Stores hashed refresh tokens for secure session management (Fix 33)';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hash of the raw token - we never store the actual token';
COMMENT ON COLUMN refresh_tokens.is_revoked IS 'Set to true when token is revoked (logout, security breach)';
