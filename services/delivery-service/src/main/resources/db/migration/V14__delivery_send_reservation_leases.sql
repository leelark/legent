-- Atomic send-rate and warm-up reservations.
-- A reservation is keyed by message/work item id and owns one rate token plus one
-- warm-up token until it is settled after provider acceptance or released on
-- pre-acceptance failure/lease expiry.

CREATE TABLE IF NOT EXISTS delivery_send_reservations (
    id VARCHAR(26) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    reservation_id VARCHAR(255) NOT NULL,
    rate_limit_key VARCHAR(500) NOT NULL,
    sender_domain VARCHAR(255) NOT NULL,
    provider_id VARCHAR(64) NOT NULL,
    recipient_domain VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    max_per_minute INT NOT NULL,
    risk_score INT NOT NULL DEFAULT 0,
    warmup_hourly_limit INT NOT NULL,
    warmup_daily_limit INT NOT NULL,
    rate_window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    warmup_hour_window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    warmup_day_window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reserved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE,
    released_at TIMESTAMP WITH TIME ZONE,
    release_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(26),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_delivery_send_reservation_scope
    ON delivery_send_reservations(tenant_id, workspace_id, reservation_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_send_reservation_rate
    ON delivery_send_reservations(tenant_id, workspace_id, rate_limit_key, status, lease_expires_at)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_send_reservation_provider
    ON delivery_send_reservations(tenant_id, workspace_id, sender_domain, provider_id, status)
    WHERE deleted_at IS NULL;
