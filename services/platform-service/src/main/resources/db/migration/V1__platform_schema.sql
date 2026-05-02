-- V1 Platform Schema

CREATE TABLE tenant_configs (
    tenant_id VARCHAR(36) PRIMARY KEY,
    theme_color VARCHAR(10) DEFAULT '#4F46E5',
    logo_url VARCHAR(255),
    timezone VARCHAR(50) DEFAULT 'UTC',
    features_json JSONB,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE webhooks (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    endpoint_url TEXT NOT NULL,
    secret_key VARCHAR(255),
    events_subscribed JSONB,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    version BIGINT DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_webhooks_tenant ON webhooks(tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE webhook_logs (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    webhook_id VARCHAR(36) REFERENCES webhooks(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    status_code INT,
    response_body TEXT,
    is_success BOOLEAN NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_webhook_logs_tenant ON webhook_logs(tenant_id);
CREATE INDEX idx_webhook_logs_webhook ON webhook_logs(webhook_id);

CREATE TABLE notifications (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36),
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    severity VARCHAR(20) DEFAULT 'INFO',
    is_read BOOLEAN DEFAULT false,
    link_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notifications_tenant_read ON notifications(tenant_id, user_id, is_read);

CREATE TABLE search_index_docs (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(36) NOT NULL,
    title VARCHAR(255),
    searchable_text TEXT,
    metadata JSONB,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_search_index_tenant ON search_index_docs(tenant_id);
CREATE INDEX idx_search_index_entity ON search_index_docs(tenant_id, entity_type, entity_id);
