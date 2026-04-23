-- =============================================
-- Audience Service Database Schema
-- Version: V1
-- Optimized for millions of subscribers
-- Multi-tenant with tenant_id isolation
-- =============================================

-- ── Subscribers ──
CREATE TABLE IF NOT EXISTS subscribers (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    subscriber_key      VARCHAR(255) NOT NULL,
    email               VARCHAR(320) NOT NULL,
    first_name          VARCHAR(128),
    last_name           VARCHAR(128),
    phone               VARCHAR(30),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    email_format        VARCHAR(10) NOT NULL DEFAULT 'HTML',
    locale              VARCHAR(10),
    timezone            VARCHAR(50),
    source              VARCHAR(50),
    custom_fields       JSONB DEFAULT '{}',
    channel_preferences JSONB DEFAULT '{}',
    last_activity_at    TIMESTAMPTZ,
    subscribed_at       TIMESTAMPTZ,
    unsubscribed_at     TIMESTAMPTZ,
    bounced_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(36),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_subscriber_tenant_key UNIQUE (tenant_id, subscriber_key)
);

CREATE INDEX idx_sub_tenant_email ON subscribers(tenant_id, email) WHERE deleted_at IS NULL;
CREATE INDEX idx_sub_tenant_status ON subscribers(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_sub_tenant_created ON subscribers(tenant_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_sub_email_lower ON subscribers(tenant_id, LOWER(email)) WHERE deleted_at IS NULL;
CREATE INDEX idx_sub_name_search ON subscribers(tenant_id, LOWER(first_name), LOWER(last_name)) WHERE deleted_at IS NULL;
CREATE INDEX idx_sub_source ON subscribers(tenant_id, source) WHERE deleted_at IS NULL;
CREATE INDEX idx_sub_custom_fields ON subscribers USING GIN (custom_fields) WHERE deleted_at IS NULL;

-- ── Subscriber Attributes (EAV) ──
CREATE TABLE IF NOT EXISTS subscriber_attributes (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    subscriber_id       VARCHAR(36) NOT NULL REFERENCES subscribers(id),
    attribute_key       VARCHAR(128) NOT NULL,
    attribute_value     TEXT,
    attribute_type      VARCHAR(20) NOT NULL DEFAULT 'STRING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sub_attr UNIQUE (subscriber_id, attribute_key)
);

CREATE INDEX idx_sub_attr_subscriber ON subscriber_attributes(subscriber_id);
CREATE INDEX idx_sub_attr_key_value ON subscriber_attributes(tenant_id, attribute_key, attribute_value);

-- ── Subscriber Preferences ──
CREATE TABLE IF NOT EXISTS subscriber_preferences (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    subscriber_id       VARCHAR(36) NOT NULL REFERENCES subscribers(id),
    email_opt_in        BOOLEAN NOT NULL DEFAULT TRUE,
    sms_opt_in          BOOLEAN NOT NULL DEFAULT FALSE,
    push_opt_in         BOOLEAN NOT NULL DEFAULT FALSE,
    frequency           VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    preferred_time      VARCHAR(5),
    preferences_data    JSONB DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sub_pref UNIQUE (subscriber_id)
);

CREATE INDEX idx_sub_pref_subscriber ON subscriber_preferences(subscriber_id);

-- ── Subscriber Lists ──
CREATE TABLE IF NOT EXISTS subscriber_lists (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         VARCHAR(2000),
    list_type           VARCHAR(20) NOT NULL DEFAULT 'PUBLICATION',
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    member_count        BIGINT NOT NULL DEFAULT 0,
    metadata            JSONB DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(36),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_list_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_list_tenant ON subscriber_lists(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_list_type ON subscriber_lists(tenant_id, list_type) WHERE deleted_at IS NULL;

-- ── List Memberships ──
CREATE TABLE IF NOT EXISTS list_memberships (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    list_id             VARCHAR(36) NOT NULL REFERENCES subscriber_lists(id),
    subscriber_id       VARCHAR(36) NOT NULL REFERENCES subscribers(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    added_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    removed_at          TIMESTAMPTZ,
    CONSTRAINT uq_list_member UNIQUE (list_id, subscriber_id)
);

CREATE INDEX idx_lm_list ON list_memberships(list_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_lm_subscriber ON list_memberships(subscriber_id);
CREATE INDEX idx_lm_tenant ON list_memberships(tenant_id);

-- ── Data Extensions ──
CREATE TABLE IF NOT EXISTS data_extensions (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         VARCHAR(2000),
    is_sendable         BOOLEAN NOT NULL DEFAULT FALSE,
    sendable_field      VARCHAR(128),
    primary_key_field   VARCHAR(128),
    record_count        BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(36),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_de_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_de_tenant ON data_extensions(tenant_id) WHERE deleted_at IS NULL;

-- ── Data Extension Fields ──
CREATE TABLE IF NOT EXISTS data_extension_fields (
    id                  VARCHAR(36) PRIMARY KEY,
    data_extension_id   VARCHAR(36) NOT NULL REFERENCES data_extensions(id),
    field_name          VARCHAR(128) NOT NULL,
    field_type          VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    is_required         BOOLEAN NOT NULL DEFAULT FALSE,
    is_primary_key      BOOLEAN NOT NULL DEFAULT FALSE,
    default_value       TEXT,
    max_length          INT,
    ordinal             INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_def_field UNIQUE (data_extension_id, field_name)
);

CREATE INDEX idx_def_de ON data_extension_fields(data_extension_id);

-- ── Data Extension Records ──
CREATE TABLE IF NOT EXISTS data_extension_records (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    data_extension_id   VARCHAR(36) NOT NULL REFERENCES data_extensions(id),
    record_data         JSONB NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_der_de ON data_extension_records(data_extension_id);
CREATE INDEX idx_der_tenant ON data_extension_records(tenant_id);
CREATE INDEX idx_der_data ON data_extension_records USING GIN (record_data);

-- ── Segments ──
CREATE TABLE IF NOT EXISTS segments (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         VARCHAR(2000),
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    segment_type        VARCHAR(20) NOT NULL DEFAULT 'FILTER',
    rules               JSONB NOT NULL DEFAULT '{}',
    member_count        BIGINT NOT NULL DEFAULT 0,
    last_evaluated_at   TIMESTAMPTZ,
    evaluation_duration_ms BIGINT,
    schedule_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(36),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_seg_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_seg_tenant ON segments(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_seg_status ON segments(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_seg_schedule ON segments(schedule_enabled) WHERE deleted_at IS NULL AND schedule_enabled = TRUE;

-- ── Segment Memberships ──
CREATE TABLE IF NOT EXISTS segment_memberships (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    segment_id          VARCHAR(36) NOT NULL REFERENCES segments(id),
    subscriber_id       VARCHAR(36) NOT NULL REFERENCES subscribers(id),
    added_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_seg_member UNIQUE (segment_id, subscriber_id)
);

CREATE INDEX idx_sm_segment ON segment_memberships(segment_id);
CREATE INDEX idx_sm_subscriber ON segment_memberships(subscriber_id);
CREATE INDEX idx_sm_tenant ON segment_memberships(tenant_id);

-- ── Suppressions ──
CREATE TABLE IF NOT EXISTS suppressions (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    email               VARCHAR(320) NOT NULL,
    suppression_type    VARCHAR(30) NOT NULL,
    reason              VARCHAR(500),
    source              VARCHAR(50),
    suppressed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(36),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT uq_suppression UNIQUE (tenant_id, email, suppression_type)
);

CREATE INDEX idx_sup_tenant_email ON suppressions(tenant_id, email) WHERE deleted_at IS NULL;
CREATE INDEX idx_sup_type ON suppressions(tenant_id, suppression_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_sup_expires ON suppressions(expires_at) WHERE expires_at IS NOT NULL AND deleted_at IS NULL;

-- ── Import Jobs ──
CREATE TABLE IF NOT EXISTS import_jobs (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    file_size           BIGINT,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    target_type         VARCHAR(30) NOT NULL DEFAULT 'SUBSCRIBER',
    target_id           VARCHAR(36),
    field_mapping       JSONB NOT NULL DEFAULT '{}',
    total_rows          BIGINT NOT NULL DEFAULT 0,
    processed_rows      BIGINT NOT NULL DEFAULT 0,
    success_rows        BIGINT NOT NULL DEFAULT 0,
    error_rows          BIGINT NOT NULL DEFAULT 0,
    errors              JSONB DEFAULT '[]',
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(36),
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_import_tenant ON import_jobs(tenant_id);
CREATE INDEX idx_import_status ON import_jobs(tenant_id, status);
