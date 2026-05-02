ALTER TABLE campaign_summaries
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(26),
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE subscriber_summaries
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(26),
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_raw_events_dedup
    ON raw_events(tenant_id, event_type, message_id, subscriber_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_raw_events_tenant_type_time
    ON raw_events(tenant_id, event_type, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_campaign_summaries_active
    ON campaign_summaries(tenant_id, campaign_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_subscriber_summaries_active
    ON subscriber_summaries(tenant_id, subscriber_id)
    WHERE deleted_at IS NULL;
