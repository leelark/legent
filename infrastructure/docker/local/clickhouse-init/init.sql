-- ClickHouse Schema Initialization for Legent Analytics
-- Run this to create the raw_events table for tracking analytics

CREATE DATABASE IF NOT EXISTS legent_analytics;

-- Raw events table for tracking data
-- Using MergeTree engine for time-series data with partitioning by month
CREATE TABLE IF NOT EXISTS legent_analytics.raw_events (
    id String,
    tenant_id String,
    event_type String,
    campaign_id Nullable(String),
    subscriber_id Nullable(String),
    message_id Nullable(String),
    user_agent Nullable(String),
    ip_address Nullable(String),
    link_url Nullable(String),
    timestamp DateTime64(3),
    metadata String DEFAULT '{}',
    -- Date-based columns for efficient partitioning and indexing
    event_date Date DEFAULT toDate(timestamp),
    event_month Date DEFAULT toStartOfMonth(timestamp)
) ENGINE = MergeTree()
PARTITION BY event_month
ORDER BY (tenant_id, event_type, timestamp)
TTL timestamp + INTERVAL 1 YEAR  -- Auto-delete data older than 1 year
SETTINGS index_granularity = 8192;

-- Materialized view for daily aggregations by campaign
CREATE MATERIALIZED VIEW IF NOT EXISTS legent_analytics.daily_campaign_stats
ENGINE = SummingMergeTree()
PARTITION BY toStartOfMonth(event_date)
ORDER BY (tenant_id, campaign_id, event_date)
AS SELECT
    tenant_id,
    campaign_id,
    toDate(timestamp) as event_date,
    event_type,
    count() as event_count
FROM legent_analytics.raw_events
WHERE campaign_id IS NOT NULL
GROUP BY tenant_id, campaign_id, toDate(timestamp), event_type;

-- Materialized view for daily aggregations by subscriber
CREATE MATERIALIZED VIEW IF NOT EXISTS legent_analytics.daily_subscriber_activity
ENGINE = SummingMergeTree()
PARTITION BY toStartOfMonth(event_date)
ORDER BY (tenant_id, subscriber_id, event_date)
AS SELECT
    tenant_id,
    subscriber_id,
    toDate(timestamp) as event_date,
    event_type,
    count() as event_count
FROM legent_analytics.raw_events
WHERE subscriber_id IS NOT NULL
GROUP BY tenant_id, subscriber_id, toDate(timestamp), event_type;

-- Table for aggregated campaign summaries (updated by tracking-service)
CREATE TABLE IF NOT EXISTS legent_analytics.campaign_summaries (
    tenant_id String,
    campaign_id String,
    event_date Date,
    opens UInt64 DEFAULT 0,
    clicks UInt64 DEFAULT 0,
    conversions UInt64 DEFAULT 0,
    bounces UInt64 DEFAULT 0,
    complaints UInt64 DEFAULT 0,
    unique_opens UInt64 DEFAULT 0,
    unique_clicks UInt64 DEFAULT 0,
    updated_at DateTime64(3) DEFAULT now64()
) ENGINE = SummingMergeTree()
PARTITION BY toStartOfMonth(event_date)
ORDER BY (tenant_id, campaign_id, event_date);
