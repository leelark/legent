-- V19: Retention indexes for terminal delivery feedback outbox cleanup.
-- Cleanup only selects terminal rows. Pending and publishing rows stay visible
-- to retry, stuck-row metrics, and operator recovery workflows.

CREATE INDEX IF NOT EXISTS idx_delivery_feedback_outbox_retention_published
    ON delivery_feedback_outbox_events (published_at, id)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_feedback_outbox_retention_failed
    ON delivery_feedback_outbox_events ((COALESCE(last_attempt_at, updated_at, created_at)), id)
    WHERE status = 'FAILED' AND deleted_at IS NULL;
