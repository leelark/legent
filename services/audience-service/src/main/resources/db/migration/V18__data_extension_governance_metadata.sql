-- V18: First-class data-extension governance metadata and audit trail.

ALTER TABLE data_extensions
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS source_system VARCHAR(128),
    ADD COLUMN IF NOT EXISTS source_reference VARCHAR(255),
    ADD COLUMN IF NOT EXISTS data_classification VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
    ADD COLUMN IF NOT EXISTS governance_notes VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS governance_reviewed_by VARCHAR(36),
    ADD COLUMN IF NOT EXISTS governance_reviewed_at TIMESTAMPTZ;

ALTER TABLE data_extension_fields
    ADD COLUMN IF NOT EXISTS data_classification VARCHAR(32) NOT NULL DEFAULT 'INTERNAL';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_de_source_type') THEN
        ALTER TABLE data_extensions
            ADD CONSTRAINT chk_de_source_type
            CHECK (source_type IN ('MANUAL', 'IMPORT', 'QUERY', 'API', 'AUTOMATION', 'INTEGRATION'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_de_data_classification') THEN
        ALTER TABLE data_extensions
            ADD CONSTRAINT chk_de_data_classification
            CHECK (data_classification IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_def_data_classification') THEN
        ALTER TABLE data_extension_fields
            ADD CONSTRAINT chk_def_data_classification
            CHECK (data_classification IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS data_extension_governance_audit (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    workspace_id        VARCHAR(36) NOT NULL,
    data_extension_id   VARCHAR(36) NOT NULL REFERENCES data_extensions(id),
    action              VARCHAR(64) NOT NULL,
    summary             VARCHAR(1000) NOT NULL,
    metadata            JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(36),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_de_governance_audit_scope
    ON data_extension_governance_audit (tenant_id, workspace_id, data_extension_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_de_source_classification
    ON data_extensions (tenant_id, workspace_id, source_type, data_classification)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_def_data_classification
    ON data_extension_fields (data_extension_id, data_classification);
