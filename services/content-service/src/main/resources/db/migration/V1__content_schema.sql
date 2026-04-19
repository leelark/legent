-- =============================================
-- Content Service Database Schema
-- Version: V1
-- Multi-tenant email template and asset management
-- =============================================

-- ── Email Templates ──
CREATE TABLE IF NOT EXISTS email_templates (
    id              VARCHAR(26) PRIMARY KEY,
    tenant_id       VARCHAR(26) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    subject         VARCHAR(500),
    html_content    TEXT,
    text_content    TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    template_type   VARCHAR(20) NOT NULL DEFAULT 'REGULAR',
    category        VARCHAR(50),
    tags            TEXT[],
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(26),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_template_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_templates_tenant ON email_templates(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_templates_status ON email_templates(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_templates_type ON email_templates(tenant_id, template_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_templates_category ON email_templates(tenant_id, category) WHERE deleted_at IS NULL;
CREATE INDEX idx_templates_tags ON email_templates USING GIN (tags) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS template_tags (
    template_id VARCHAR(26) NOT NULL REFERENCES email_templates(id) ON DELETE CASCADE,
    tag VARCHAR(255) NOT NULL,
    PRIMARY KEY (template_id, tag)
);

-- ── Content Blocks ──
CREATE TABLE IF NOT EXISTS content_blocks (
    id              VARCHAR(26) PRIMARY KEY,
    tenant_id       VARCHAR(26) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    block_type      VARCHAR(50) NOT NULL,
    content         TEXT NOT NULL,
    styles          JSONB DEFAULT '{}',
    settings        JSONB DEFAULT '{}',
    is_global       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(26),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_block_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_blocks_tenant ON content_blocks(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_blocks_type ON content_blocks(tenant_id, block_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_blocks_global ON content_blocks(is_global) WHERE deleted_at IS NULL;

-- ── Assets ──
CREATE TABLE IF NOT EXISTS assets (
    id              VARCHAR(26) PRIMARY KEY,
    tenant_id       VARCHAR(26) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    storage_path    VARCHAR(500) NOT NULL,
    storage_bucket  VARCHAR(100) NOT NULL,
    alt_text        VARCHAR(500),
    tags            TEXT[],
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(26),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_assets_tenant ON assets(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_assets_content_type ON assets(tenant_id, content_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_assets_tags ON assets USING GIN (tags) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS asset_tags (
    asset_id VARCHAR(26) NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    tag VARCHAR(255) NOT NULL,
    PRIMARY KEY (asset_id, tag)
);

-- ── Template Versions ──
CREATE TABLE IF NOT EXISTS template_versions (
    id              VARCHAR(26) PRIMARY KEY,
    tenant_id       VARCHAR(26) NOT NULL,
    template_id     VARCHAR(26) NOT NULL REFERENCES email_templates(id),
    version_number  INT NOT NULL,
    subject         VARCHAR(500),
    html_content    TEXT,
    text_content    TEXT,
    changes         TEXT,
    is_published    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(26),
    CONSTRAINT uq_template_version UNIQUE (template_id, version_number)
);

CREATE INDEX idx_template_versions_template ON template_versions(template_id);
CREATE INDEX idx_template_versions_published ON template_versions(template_id, is_published);