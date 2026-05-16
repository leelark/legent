-- V11: Raw tracking retention guardrails.
-- PostgreSQL keeps the transactional ingest copy bounded while ClickHouse keeps
-- the BI-grade raw stream partitioned by event month with its own TTL contract.

CREATE INDEX IF NOT EXISTS idx_raw_events_retention_cutoff
    ON raw_events ("timestamp");

CREATE TABLE IF NOT EXISTS tracking_retention_policies (
    dataset VARCHAR(128) PRIMARY KEY,
    retention_days INTEGER NOT NULL CHECK (retention_days > 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO tracking_retention_policies (dataset, retention_days)
VALUES ('raw_events_postgres', 30)
ON CONFLICT (dataset) DO UPDATE
SET retention_days = EXCLUDED.retention_days,
    updated_at = NOW();

CREATE OR REPLACE FUNCTION purge_expired_raw_events(batch_size INTEGER DEFAULT 10000)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    WITH expired AS (
        SELECT id
        FROM raw_events
        WHERE "timestamp" < NOW() - (
            SELECT make_interval(days => retention_days)
            FROM tracking_retention_policies
            WHERE dataset = 'raw_events_postgres'
        )
        ORDER BY "timestamp"
        LIMIT batch_size
    )
    DELETE FROM raw_events r
    USING expired
    WHERE r.id = expired.id;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$;
