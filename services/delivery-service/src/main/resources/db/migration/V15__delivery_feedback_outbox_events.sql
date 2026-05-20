-- V15: Durable delivery feedback outbox for DB/Kafka consistency.

CREATE TABLE IF NOT EXISTS delivery_feedback_outbox_events (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    environment_id VARCHAR(64),
    actor_id VARCHAR(64),
    ownership_scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE',
    topic VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    source VARCHAR(128) NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    retry_count INT NOT NULL DEFAULT 0,
    correlation_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    message_id VARCHAR(255) NOT NULL,
    campaign_id VARCHAR(64),
    job_id VARCHAR(64),
    batch_id VARCHAR(64),
    subscriber_id VARCHAR(64),
    provider_id VARCHAR(64),
    recipient_email VARCHAR(320),
    sender_domain VARCHAR(255),
    transition_key VARCHAR(512) NOT NULL,
    partition_key VARCHAR(512) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 8,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(26),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_delivery_feedback_outbox_transition
    ON delivery_feedback_outbox_events (tenant_id, workspace_id, event_type, transition_key)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_delivery_feedback_outbox_idempotency
    ON delivery_feedback_outbox_events (tenant_id, workspace_id, event_type, idempotency_key)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_feedback_outbox_ready
    ON delivery_feedback_outbox_events (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'PUBLISHING') AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_feedback_outbox_workspace_status
    ON delivery_feedback_outbox_events (tenant_id, workspace_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_feedback_outbox_message
    ON delivery_feedback_outbox_events (tenant_id, workspace_id, message_id, event_type)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_feedback_outbox_published
    ON delivery_feedback_outbox_events (published_at)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
