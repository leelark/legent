-- V1 Deliverability Schema

-- Domains table for domain configuration
CREATE TABLE domains (
    id BIGSERIAL PRIMARY KEY,
    domain VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    spf_status VARCHAR(20),
    dkim_status VARCHAR(20),
    dmarc_status VARCHAR(20),
    last_checked TIMESTAMP WITH TIME ZONE
);

CREATE TABLE sender_domains (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    domain_name VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT false,
    spf_verified BOOLEAN DEFAULT false,
    dkim_verified BOOLEAN DEFAULT false,
    dmarc_verified BOOLEAN DEFAULT false,
    last_verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_sender_domains_tenant ON sender_domains(tenant_id, domain_name);

CREATE TABLE domain_reputations (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    domain_id VARCHAR(36) REFERENCES sender_domains(id) ON DELETE CASCADE,
    reputation_score INT DEFAULT 100, -- 0 to 100
    hard_bounce_rate DECIMAL(5,4) DEFAULT 0,
    complaint_rate DECIMAL(5,4) DEFAULT 0,
    calculated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reputation_scores (
    id BIGSERIAL PRIMARY KEY,
    domain VARCHAR(255) NOT NULL UNIQUE,
    score FLOAT NOT NULL DEFAULT 100.00,
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE suppression_list (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    email VARCHAR(320) NOT NULL,
    reason VARCHAR(50) NOT NULL, -- HARD_BOUNCE, COMPLAINT, UNSUBSCRIBE
    source VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_suppressions_tenant_email ON suppression_list(tenant_id, email);
