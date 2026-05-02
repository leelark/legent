-- V3: Add webhook_retries table for persistent retry mechanism
-- AUDIT-015: Implements dead-letter queue for failed webhooks

CREATE TABLE webhook_retries (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    webhook_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    error_message TEXT,
    retry_count INT DEFAULT 0 NOT NULL,
    max_retries INT DEFAULT 3 NOT NULL,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT
);

CREATE INDEX idx_webhook_retries_pending ON webhook_retries(status, next_retry_at) WHERE status = 'PENDING';
CREATE INDEX idx_webhook_retries_tenant ON webhook_retries(tenant_id, status);
CREATE INDEX idx_webhook_retries_webhook ON webhook_retries(webhook_id, status);
