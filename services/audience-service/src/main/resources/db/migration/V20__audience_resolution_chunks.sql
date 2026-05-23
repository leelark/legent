CREATE TABLE IF NOT EXISTS audience_resolution_chunks (
    id                          VARCHAR(36) PRIMARY KEY,
    tenant_id                   VARCHAR(36) NOT NULL,
    workspace_id                VARCHAR(36) NOT NULL,
    campaign_id                 VARCHAR(36) NOT NULL,
    job_id                      VARCHAR(36) NOT NULL,
    chunk_id                    VARCHAR(128) NOT NULL,
    chunk_index                 INT NOT NULL,
    chunk_size                  INT NOT NULL,
    total_chunks                INT NOT NULL,
    total_resolved_subscribers  INT NOT NULL,
    last_chunk                  BOOLEAN NOT NULL DEFAULT FALSE,
    subscriber_payload          JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(36),
    deleted_at                  TIMESTAMPTZ,
    version                     BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_audience_resolution_chunks_job_chunk
    ON audience_resolution_chunks (tenant_id, workspace_id, job_id, chunk_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_audience_resolution_chunks_job_page
    ON audience_resolution_chunks (tenant_id, workspace_id, job_id, chunk_index)
    WHERE deleted_at IS NULL;
