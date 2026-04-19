-- V1 Delivery Schema

CREATE TABLE smtp_providers (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'SMTP',
    host VARCHAR(255),
    port INT,
    username VARCHAR(255),
    password_hash VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    priority INT DEFAULT 1,
    max_send_rate INT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_smtp_providers_tenant ON smtp_providers(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_smtp_providers_active ON smtp_providers(tenant_id, is_active) WHERE deleted_at IS NULL;

CREATE TABLE ip_pools (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    ips TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_ip_pools_tenant ON ip_pools(tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE routing_rules (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    sender_domain VARCHAR(255) NOT NULL,
    provider_id VARCHAR(36) REFERENCES smtp_providers(id),
    ip_pool_id VARCHAR(36) REFERENCES ip_pools(id),
    fallback_provider_id VARCHAR(36) REFERENCES smtp_providers(id),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_routing_domain ON routing_rules(tenant_id, sender_domain)
    WHERE deleted_at IS NULL;

CREATE TABLE message_logs (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    message_id VARCHAR(255) NOT NULL,
    campaign_id VARCHAR(36),
    subscriber_id VARCHAR(36),
    email VARCHAR(320) NOT NULL,
    provider_id VARCHAR(36),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    attempt_count INT DEFAULT 0,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    provider_response TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_message_logs_tenant_msg ON message_logs(tenant_id, message_id);
CREATE INDEX idx_message_logs_tenant_campaign ON message_logs(tenant_id, campaign_id);
CREATE INDEX idx_message_logs_email ON message_logs(tenant_id, email);
CREATE INDEX idx_message_logs_status ON message_logs(tenant_id, status);
CREATE INDEX idx_message_logs_retry ON message_logs(next_retry_at)
    WHERE status = 'PENDING' AND next_retry_at IS NOT NULL;

CREATE TABLE suppression_signals (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    email VARCHAR(320) NOT NULL,
    type VARCHAR(50) NOT NULL,
    reason TEXT,
    source_message_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_suppressions_email ON suppression_signals(tenant_id, email, type);
