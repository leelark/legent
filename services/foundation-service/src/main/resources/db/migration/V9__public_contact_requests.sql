-- V9: public contact request intake

CREATE TABLE IF NOT EXISTS public_contact_requests (
    id              VARCHAR(26) PRIMARY KEY,
    name            VARCHAR(120),
    work_email      VARCHAR(255) NOT NULL,
    company         VARCHAR(160) NOT NULL,
    interest        VARCHAR(120),
    message         VARCHAR(2000) NOT NULL,
    source_page     VARCHAR(120),
    status          VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(26),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_public_contact_requests_status_created
    ON public_contact_requests(status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_public_contact_requests_email
    ON public_contact_requests(LOWER(work_email))
    WHERE deleted_at IS NULL;
