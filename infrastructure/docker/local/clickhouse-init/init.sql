-- ClickHouse Schema Initialization for Legent Analytics
-- Run this to create the raw_events table for tracking analytics

CREATE DATABASE IF NOT EXISTS legent_analytics;

-- Raw events table for tracking data
-- Using MergeTree engine for time-series data with partitioning by month
CREATE TABLE IF NOT EXISTS legent_analytics.raw_events (
    id String,
    tenant_id String,
    workspace_id String,
    event_type LowCardinality(String),
    campaign_id Nullable(String),
    subscriber_id Nullable(String),
    message_id Nullable(String),
    experiment_id Nullable(String),
    variant_id Nullable(String),
    holdout UInt8 DEFAULT 0,
    experiment_scope Nullable(String),
    workflow_id Nullable(String),
    workflow_version Nullable(Int32),
    workflow_run_id Nullable(String),
    step_id Nullable(String),
    path_id Nullable(String),
    goal_id Nullable(String),
    user_agent Nullable(String),
    ip_address Nullable(String),
    link_url Nullable(String),
    timestamp DateTime64(3, 'UTC'),
    metadata String DEFAULT '{}',
    -- Date-based columns for efficient partitioning and indexing
    event_date Date DEFAULT toDate(timestamp),
    event_month Date DEFAULT toStartOfMonth(timestamp)
) ENGINE = MergeTree()
PARTITION BY event_month
ORDER BY (tenant_id, workspace_id, campaign_id, workflow_id, step_id, experiment_id, variant_id, event_type, timestamp, id)
TTL toDateTime(timestamp) + INTERVAL 1 YEAR  -- Auto-delete data older than 1 year
SETTINGS allow_nullable_key = 1, index_granularity = 8192;

-- Repair stale local volumes created before workspace, experiment, and journey lineage existed.
ALTER TABLE legent_analytics.raw_events ADD COLUMN IF NOT EXISTS workspace_id String AFTER tenant_id;
ALTER TABLE legent_analytics.raw_events ADD COLUMN IF NOT EXISTS experiment_id Nullable(String) AFTER message_id;
ALTER TABLE legent_analytics.raw_events ADD COLUMN IF NOT EXISTS variant_id Nullable(String) AFTER experiment_id;
ALTER TABLE legent_analytics.raw_events ADD COLUMN IF NOT EXISTS holdout UInt8 DEFAULT 0 AFTER variant_id;
ALTER TABLE legent_analytics.raw_events ADD COLUMN IF NOT EXISTS experiment_scope Nullable(String) AFTER holdout;
ALTER TABLE legent_analytics.raw_events ADD COLUMN IF NOT EXISTS workflow_id Nullable(String) AFTER experiment_scope;
ALTER TABLE legent_analytics.raw_events ADD COLUMN IF NOT EXISTS workflow_version Nullable(Int32) AFTER workflow_id;
ALTER TABLE legent_analytics.raw_events ADD COLUMN IF NOT EXISTS workflow_run_id Nullable(String) AFTER workflow_version;
ALTER TABLE legent_analytics.raw_events ADD COLUMN IF NOT EXISTS step_id Nullable(String) AFTER workflow_run_id;
ALTER TABLE legent_analytics.raw_events ADD COLUMN IF NOT EXISTS path_id Nullable(String) AFTER step_id;
ALTER TABLE legent_analytics.raw_events ADD COLUMN IF NOT EXISTS goal_id Nullable(String) AFTER path_id;

-- Materialized view for daily aggregations by campaign
CREATE MATERIALIZED VIEW IF NOT EXISTS legent_analytics.daily_campaign_stats
ENGINE = SummingMergeTree()
PARTITION BY toStartOfMonth(event_date)
ORDER BY (tenant_id, workspace_id, campaign_id, event_date, event_type)
AS SELECT
    tenant_id,
    workspace_id,
    assumeNotNull(campaign_id) as campaign_id,
    toDate(timestamp) as event_date,
    event_type,
    count() as event_count
FROM legent_analytics.raw_events
WHERE campaign_id IS NOT NULL
GROUP BY tenant_id, workspace_id, campaign_id, toDate(timestamp), event_type;

-- Materialized view for daily aggregations by subscriber
CREATE MATERIALIZED VIEW IF NOT EXISTS legent_analytics.daily_subscriber_activity
ENGINE = SummingMergeTree()
PARTITION BY toStartOfMonth(event_date)
ORDER BY (tenant_id, workspace_id, subscriber_id, event_date, event_type)
AS SELECT
    tenant_id,
    workspace_id,
    assumeNotNull(subscriber_id) as subscriber_id,
    toDate(timestamp) as event_date,
    event_type,
    count() as event_count
FROM legent_analytics.raw_events
WHERE subscriber_id IS NOT NULL
GROUP BY tenant_id, workspace_id, subscriber_id, toDate(timestamp), event_type;

-- Table for aggregated campaign summaries (updated by tracking-service)
CREATE TABLE IF NOT EXISTS legent_analytics.campaign_summaries (
    tenant_id String,
    workspace_id String,
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
ORDER BY (tenant_id, workspace_id, campaign_id, event_date);
