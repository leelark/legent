-- V15: Indexes for keyset audience candidate resolution queries.

CREATE INDEX IF NOT EXISTS idx_subscribers_candidate_keyset
    ON subscribers (tenant_id, workspace_id, id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_list_memberships_candidate_active
    ON list_memberships (tenant_id, workspace_id, list_id, subscriber_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_segment_memberships_candidate
    ON segment_memberships (tenant_id, workspace_id, segment_id, subscriber_id);
