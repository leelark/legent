-- V1 Campaign Schema

CREATE TABLE campaigns (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    preheader VARCHAR(255),
    sender_profile_id VARCHAR(36),
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    type VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_campaigns_tenant ON campaigns(tenant_id);
CREATE INDEX idx_campaigns_status ON campaigns(tenant_id, status) WHERE deleted_at IS NULL;

CREATE TABLE campaign_audiences (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    campaign_id VARCHAR(36) NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    audience_type VARCHAR(50) NOT NULL,
    audience_id VARCHAR(36) NOT NULL,
    action VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_campaign_audiences_campaign ON campaign_audiences(tenant_id, campaign_id);

CREATE TABLE send_jobs (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    campaign_id VARCHAR(36) NOT NULL REFERENCES campaigns(id),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    scheduled_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    total_target BIGINT DEFAULT 0,
    total_sent BIGINT DEFAULT 0,
    total_failed BIGINT DEFAULT 0,
    total_bounced BIGINT DEFAULT 0,
    total_suppressed BIGINT DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_send_jobs_tenant_campaign ON send_jobs(tenant_id, campaign_id);
CREATE INDEX idx_send_jobs_status ON send_jobs(tenant_id, status);

CREATE TABLE send_batches (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    job_id VARCHAR(36) NOT NULL REFERENCES send_jobs(id) ON DELETE CASCADE,
    campaign_id VARCHAR(36),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    domain VARCHAR(255),
    batch_size INT NOT NULL,
    processed_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    failure_count INT DEFAULT 0,
    payload JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_send_batches_job ON send_batches(tenant_id, job_id);
CREATE INDEX idx_send_batches_status ON send_batches(tenant_id, status);

CREATE TABLE delivery_logs (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    job_id VARCHAR(36) NOT NULL REFERENCES send_jobs(id) ON DELETE CASCADE,
    batch_id VARCHAR(36) NOT NULL REFERENCES send_batches(id) ON DELETE CASCADE,
    subscriber_id VARCHAR(36) NOT NULL,
    email VARCHAR(320) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'SENT',
    retry_count INT DEFAULT 0,
    provider_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_delivery_logs_job ON delivery_logs(tenant_id, job_id);
CREATE INDEX idx_delivery_logs_subscriber ON delivery_logs(tenant_id, subscriber_id);

CREATE TABLE throttling_rules (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    domain VARCHAR(255) NOT NULL,
    max_emails INT NOT NULL,
    time_window_seconds INT NOT NULL,
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_throttling_rules_domain ON throttling_rules(tenant_id, domain)
    WHERE deleted_at IS NULL;
