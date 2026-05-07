-- V7: Email Studio enterprise content model

ALTER TABLE template_versions ADD COLUMN IF NOT EXISTS validation_status VARCHAR(32);
ALTER TABLE template_versions ADD COLUMN IF NOT EXISTS validation_report JSONB DEFAULT '{}';

CREATE TABLE IF NOT EXISTS content_block_versions (
    id              VARCHAR(26) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    block_id        VARCHAR(36) NOT NULL REFERENCES content_blocks(id) ON DELETE CASCADE,
    version_number  INT NOT NULL,
    content         TEXT NOT NULL,
    styles          JSONB DEFAULT '{}',
    settings        JSONB DEFAULT '{}',
    changes         TEXT,
    is_published    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(36),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_content_block_version UNIQUE (block_id, version_number)
);
CREATE INDEX IF NOT EXISTS idx_content_block_versions_block ON content_block_versions(block_id, tenant_id);

CREATE TABLE IF NOT EXISTS content_snippets (
    id              VARCHAR(26) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    snippet_key     VARCHAR(128) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    snippet_type    VARCHAR(32) NOT NULL DEFAULT 'HTML',
    content         TEXT NOT NULL,
    description     TEXT,
    is_global       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(36),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_content_snippet_key UNIQUE (tenant_id, snippet_key)
);
CREATE INDEX IF NOT EXISTS idx_content_snippets_tenant ON content_snippets(tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS personalization_tokens (
    id              VARCHAR(26) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    token_key       VARCHAR(128) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    description     TEXT,
    source_type     VARCHAR(64) NOT NULL DEFAULT 'SUBSCRIBER',
    data_path       VARCHAR(255),
    default_value   TEXT,
    sample_value    TEXT,
    required        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(36),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_personalization_token UNIQUE (tenant_id, token_key)
);
CREATE INDEX IF NOT EXISTS idx_personalization_tokens_tenant ON personalization_tokens(tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS dynamic_content_rules (
    id              VARCHAR(26) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    template_id     VARCHAR(36) NOT NULL REFERENCES email_templates(id) ON DELETE CASCADE,
    slot_key        VARCHAR(128) NOT NULL DEFAULT 'main',
    name            VARCHAR(255) NOT NULL,
    priority        INT NOT NULL DEFAULT 100,
    condition_field VARCHAR(128),
    operator        VARCHAR(32) NOT NULL DEFAULT 'ALWAYS',
    condition_value TEXT,
    html_content    TEXT,
    text_content    TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(36),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_dynamic_rules_template ON dynamic_content_rules(tenant_id, template_id, slot_key, priority) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS brand_kits (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    logo_url            TEXT,
    primary_color       VARCHAR(32),
    secondary_color     VARCHAR(32),
    font_family         VARCHAR(255),
    footer_html         TEXT,
    legal_text          TEXT,
    default_from_name   VARCHAR(255),
    default_from_email  VARCHAR(320),
    is_default          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(36),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_brand_kit_name UNIQUE (tenant_id, name)
);
CREATE INDEX IF NOT EXISTS idx_brand_kits_default ON brand_kits(tenant_id, is_default) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS landing_pages (
    id              VARCHAR(26) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    html_content    TEXT,
    metadata        JSONB DEFAULT '{}',
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(36),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_landing_page_slug UNIQUE (slug)
);
CREATE INDEX IF NOT EXISTS idx_landing_pages_public ON landing_pages(slug, status) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS template_test_send_records (
    id                  VARCHAR(26) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    template_id         VARCHAR(36) NOT NULL REFERENCES email_templates(id) ON DELETE CASCADE,
    recipient_email     VARCHAR(320) NOT NULL,
    recipient_group     VARCHAR(255),
    subject             VARCHAR(500),
    status              VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    message_id          VARCHAR(255),
    variables_json      JSONB DEFAULT '{}',
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(36),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_test_sends_template ON template_test_send_records(tenant_id, template_id, created_at DESC);

CREATE TABLE IF NOT EXISTS render_validation_reports (
    id              VARCHAR(26) PRIMARY KEY,
    tenant_id       VARCHAR(36) NOT NULL,
    template_id     VARCHAR(36),
    status          VARCHAR(32) NOT NULL,
    report_json     JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(36),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_render_reports_template ON render_validation_reports(tenant_id, template_id, created_at DESC);
