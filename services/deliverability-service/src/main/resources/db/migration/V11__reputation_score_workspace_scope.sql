-- Scope legacy reputation score compatibility rows by tenant/workspace.
-- V7 added tenant_id/workspace_id/source columns; this migration removes the
-- original global domain uniqueness constraint so same-domain reputation rows
-- can coexist per workspace and over time.

ALTER TABLE reputation_scores
    DROP CONSTRAINT IF EXISTS reputation_scores_domain_key;

CREATE INDEX IF NOT EXISTS idx_reputation_scores_scope_latest
    ON reputation_scores (tenant_id, workspace_id, domain, last_updated DESC);
