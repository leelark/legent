-- V4: Hourly aggregation table for analytics
CREATE TABLE IF NOT EXISTS tracking_hourly_agg (
    event_type VARCHAR(32) NOT NULL,
    hour TIMESTAMPTZ NOT NULL,
    count INTEGER NOT NULL,
    PRIMARY KEY (event_type, hour)
);
