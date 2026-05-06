-- V8: dynamic public marketing content

CREATE TABLE IF NOT EXISTS public_contents (
    id              VARCHAR(26) PRIMARY KEY,
    tenant_id       VARCHAR(36),
    workspace_id    VARCHAR(36),
    content_type    VARCHAR(32) NOT NULL,
    page_key        VARCHAR(64) NOT NULL,
    slug            VARCHAR(128),
    title           VARCHAR(255),
    status          VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    seo_meta        JSONB NOT NULL DEFAULT '{}'::jsonb,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(26),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_public_contents_lookup
    ON public_contents(content_type, page_key, status)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_public_contents_scope
    ON public_contents(COALESCE(tenant_id, ''), COALESCE(workspace_id, ''), content_type, page_key, COALESCE(slug, ''))
    WHERE deleted_at IS NULL;
