-- V9: Durable tracking outbox for DB/Kafka consistency.

CREATE TABLE IF NOT EXISTS tracking_outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    workspace_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    message_id VARCHAR(36),
    idempotency_key VARCHAR(255),
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_tracking_outbox_ready
    ON tracking_outbox_events (status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_tracking_outbox_tenant_workspace
    ON tracking_outbox_events (tenant_id, workspace_id, event_type, created_at DESC);
