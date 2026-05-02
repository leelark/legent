-- =============================================
-- Tracking Service Enhanced Tracking Schema
-- Version: V2
-- Adds geo tracking, device tracking, and fraud detection
-- =============================================

-- ── Add geo and device fields to raw_events ──
ALTER TABLE raw_events ADD COLUMN IF NOT EXISTS country_code VARCHAR(2);
ALTER TABLE raw_events ADD COLUMN IF NOT EXISTS city VARCHAR(100);
ALTER TABLE raw_events ADD COLUMN IF NOT EXISTS region VARCHAR(100);
ALTER TABLE raw_events ADD COLUMN IF NOT EXISTS latitude DECIMAL(10, 8);
ALTER TABLE raw_events ADD COLUMN IF NOT EXISTS longitude DECIMAL(11, 8);
ALTER TABLE raw_events ADD COLUMN IF NOT EXISTS device_type VARCHAR(20);
ALTER TABLE raw_events ADD COLUMN IF NOT EXISTS browser VARCHAR(50);
ALTER TABLE raw_events ADD COLUMN IF NOT EXISTS os VARCHAR(50);
ALTER TABLE raw_events ADD COLUMN IF NOT EXISTS is_bot BOOLEAN DEFAULT FALSE;
ALTER TABLE raw_events ADD COLUMN IF NOT EXISTS fraud_score INT DEFAULT 0;
ALTER TABLE raw_events ADD COLUMN IF NOT EXISTS is_duplicate BOOLEAN DEFAULT FALSE;

-- ── Create indexes for analytics queries ──
CREATE INDEX IF NOT EXISTS idx_raw_events_geo ON raw_events(tenant_id, country_code) WHERE country_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_raw_events_device ON raw_events(tenant_id, device_type) WHERE device_type IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_raw_events_browser ON raw_events(tenant_id, browser) WHERE browser IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_raw_events_bot ON raw_events(tenant_id, is_bot) WHERE is_bot = TRUE;

-- ── Create campaign summary materialized view ──
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_campaign_summaries AS
SELECT 
    tenant_id,
    campaign_id,
    COUNT(*) FILTER (WHERE event_type = 'OPEN') as opens,
    COUNT(*) FILTER (WHERE event_type = 'CLICK') as clicks,
    COUNT(*) FILTER (WHERE event_type = 'CONVERSION') as conversions,
    COUNT(DISTINCT subscriber_id) FILTER (WHERE event_type = 'OPEN') as unique_opens,
    COUNT(DISTINCT subscriber_id) FILTER (WHERE event_type = 'CLICK') as unique_clicks,
    MAX(timestamp) as last_event_at
FROM raw_events
WHERE campaign_id IS NOT NULL
GROUP BY tenant_id, campaign_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_campaign_summaries ON mv_campaign_summaries(tenant_id, campaign_id);

-- ── Create hourly aggregation table ──
CREATE TABLE IF NOT EXISTS hourly_event_stats (
    id SERIAL PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    hour TIMESTAMP WITH TIME ZONE NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    campaign_id VARCHAR(36),
    total_count INT NOT NULL DEFAULT 0,
    unique_subscribers INT NOT NULL DEFAULT 0,
    bot_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, hour, event_type, campaign_id)
);

CREATE INDEX IF NOT EXISTS idx_hourly_stats_tenant ON hourly_event_stats(tenant_id, hour);
CREATE INDEX IF NOT EXISTS idx_hourly_stats_campaign ON hourly_event_stats(tenant_id, campaign_id, hour);

-- ── Fraud detection rules table ──
CREATE TABLE IF NOT EXISTS fraud_detection_rules (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36),
    rule_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    threshold_value DECIMAL(10, 2),
    time_window_seconds INT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ── Bot patterns table ──
CREATE TABLE IF NOT EXISTS bot_patterns (
    id VARCHAR(36) PRIMARY KEY,
    pattern_type VARCHAR(50) NOT NULL,
    pattern_value VARCHAR(500) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Insert default bot patterns
INSERT INTO bot_patterns (id, pattern_type, pattern_value) VALUES
    ('01H', 'USER_AGENT', 'bot'),
    ('01I', 'USER_AGENT', 'crawler'),
    ('01J', 'USER_AGENT', 'spider'),
    ('01K', 'USER_AGENT', 'scraper'),
    ('01L', 'USER_AGENT', 'Googlebot'),
    ('01M', 'USER_AGENT', 'Bingbot'),
    ('01N', 'IP_RANGE', '10.0.0.0/8')
ON CONFLICT DO NOTHING;
