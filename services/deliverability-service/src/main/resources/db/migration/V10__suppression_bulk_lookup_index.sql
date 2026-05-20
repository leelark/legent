-- V10: Support tenant/workspace-scoped case-insensitive suppression candidate checks.
-- The query path is used by audience resolution and must not require a full workspace suppression fetch.
CREATE INDEX IF NOT EXISTS idx_suppression_list_workspace_lower_email_active
    ON suppression_list (tenant_id, workspace_id, lower(trim(email)))
    WHERE expires_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_suppression_list_workspace_lower_email_expiring
    ON suppression_list (tenant_id, workspace_id, lower(trim(email)), expires_at)
    WHERE expires_at IS NOT NULL;
