-- =============================================
-- Audience Service Consent and Double Opt-In Schema
-- Version: V2
-- GDPR/Privacy compliance support
-- =============================================

-- ── Consent Records ──
CREATE TABLE IF NOT EXISTS consent_records (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    subscriber_id       VARCHAR(36) NOT NULL REFERENCES subscribers(id) ON DELETE CASCADE,
    consent_type        VARCHAR(50) NOT NULL,
    consent_given       BOOLEAN NOT NULL DEFAULT FALSE,
    consent_source      VARCHAR(50) NOT NULL DEFAULT 'WEB_FORM',
    ip_address          VARCHAR(45),
    user_agent          TEXT,
    consent_date        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    withdrawn_date      TIMESTAMPTZ,
    legal_basis         VARCHAR(50) NOT NULL DEFAULT 'CONSENT',
    privacy_version     VARCHAR(20),
    notes               VARCHAR(1000),
    created_by          VARCHAR(36),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_consent_subscriber_type UNIQUE (subscriber_id, consent_type)
);

CREATE INDEX idx_consent_tenant ON consent_records(tenant_id) WHERE withdrawn_date IS NULL;
CREATE INDEX idx_consent_subscriber ON consent_records(subscriber_id);
CREATE INDEX idx_consent_type ON consent_records(tenant_id, consent_type);
CREATE INDEX idx_consent_date ON consent_records(consent_date);

-- ── Double Opt-In Tokens ──
CREATE TABLE IF NOT EXISTS double_optin_tokens (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    subscriber_id       VARCHAR(36) NOT NULL REFERENCES subscribers(id) ON DELETE CASCADE,
    token_hash          VARCHAR(64) NOT NULL,
    email               VARCHAR(320) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at             TIMESTAMPTZ,
    confirmed_at        TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ NOT NULL,
    ip_address          VARCHAR(45),
    user_agent          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_double_optin_token UNIQUE (token_hash)
);

CREATE INDEX idx_double_optin_tenant ON double_optin_tokens(tenant_id);
CREATE INDEX idx_double_optin_subscriber ON double_optin_tokens(subscriber_id);
CREATE INDEX idx_double_optin_status ON double_optin_tokens(tenant_id, status) WHERE status = 'PENDING';
CREATE INDEX idx_double_optin_expires ON double_optin_tokens(expires_at) WHERE status = 'PENDING';

-- ── Consent Audit Log ──
CREATE TABLE IF NOT EXISTS consent_audit_log (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    subscriber_id       VARCHAR(36) NOT NULL REFERENCES subscribers(id) ON DELETE CASCADE,
    action              VARCHAR(50) NOT NULL,
    consent_type        VARCHAR(50) NOT NULL,
    old_value           JSONB,
    new_value           JSONB,
    ip_address          VARCHAR(45),
    user_agent          TEXT,
    performed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    performed_by        VARCHAR(36)
);

CREATE INDEX idx_consent_audit_tenant ON consent_audit_log(tenant_id);
CREATE INDEX idx_consent_audit_subscriber ON consent_audit_log(subscriber_id);
CREATE INDEX idx_consent_audit_action ON consent_audit_log(action);
CREATE INDEX idx_consent_audit_date ON consent_audit_log(performed_at);

-- ── Subscriber Merge History ──
CREATE TABLE IF NOT EXISTS subscriber_merge_history (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    source_subscriber_id VARCHAR(36) NOT NULL REFERENCES subscribers(id),
    target_subscriber_id VARCHAR(36) NOT NULL REFERENCES subscribers(id),
    merged_fields       JSONB NOT NULL DEFAULT '{}',
    conflict_resolution JSONB NOT NULL DEFAULT '{}',
    merged_by           VARCHAR(36),
    merged_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notes               VARCHAR(1000)
);

CREATE INDEX idx_merge_history_tenant ON subscriber_merge_history(tenant_id);
CREATE INDEX idx_merge_history_source ON subscriber_merge_history(source_subscriber_id);
CREATE INDEX idx_merge_history_target ON subscriber_merge_history(target_subscriber_id);

-- ── Add double_opt_in_confirmed column to subscribers ──
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS double_opt_in_confirmed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE subscribers ADD COLUMN IF NOT EXISTS double_opt_in_confirmed_at TIMESTAMPTZ;
