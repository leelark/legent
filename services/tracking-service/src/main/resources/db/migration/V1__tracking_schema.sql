-- V1 Tracking Schema

-- In a fully scaled environment, raw_events should reside in ClickHouse.
-- For standard scaling inside Postgres, we use partitioned tables (omitted syntax here for simplicity)
CREATE TABLE raw_events (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL, -- OPEN, CLICK, CONVERSION
    campaign_id VARCHAR(36),
    subscriber_id VARCHAR(36),
    message_id VARCHAR(36),
    user_agent TEXT,
    ip_address VARCHAR(45),
    link_url TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

CREATE INDEX idx_raw_events_tenant_camp ON raw_events(tenant_id, campaign_id, event_type);
CREATE INDEX idx_raw_events_timestamp ON raw_events(tenant_id, timestamp);

CREATE TABLE campaign_summaries (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    campaign_id VARCHAR(36) NOT NULL,
    total_sends BIGINT DEFAULT 0,
    total_opens BIGINT DEFAULT 0,
    total_clicks BIGINT DEFAULT 0,
    total_conversions BIGINT DEFAULT 0,
    total_bounces BIGINT DEFAULT 0,
    unique_opens BIGINT DEFAULT 0,
    unique_clicks BIGINT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_camp_summaries_tenant_camp ON campaign_summaries(tenant_id, campaign_id);

CREATE TABLE subscriber_summaries (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    subscriber_id VARCHAR(36) NOT NULL,
    total_received BIGINT DEFAULT 0,
    total_opens BIGINT DEFAULT 0,
    total_clicks BIGINT DEFAULT 0,
    last_engaged_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_sub_summaries_tenant_sub ON subscriber_summaries(tenant_id, subscriber_id);
CREATE INDEX idx_sub_summaries_engaged ON subscriber_summaries(tenant_id, last_engaged_at DESC);
