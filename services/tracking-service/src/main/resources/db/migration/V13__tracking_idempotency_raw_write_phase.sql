-- Track the raw-write boundary separately from full tracking-event processing.
ALTER TABLE tracking_event_idempotency
    ADD COLUMN IF NOT EXISTS raw_written_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_tracking_event_idempotency_raw_written_pending
    ON tracking_event_idempotency (tenant_id, workspace_id, raw_written_at ASC)
    WHERE raw_written_at IS NOT NULL
      AND processed_at IS NULL;
