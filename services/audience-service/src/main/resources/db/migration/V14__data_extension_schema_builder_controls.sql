-- V14: Salesforce-style data extension schema builder metadata.

ALTER TABLE data_extensions
    ADD COLUMN IF NOT EXISTS retention_days INT,
    ADD COLUMN IF NOT EXISTS retention_action VARCHAR(32) DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS relationship_json JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_de_sendable
    ON data_extensions (tenant_id, is_sendable)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_der_de_tenant_created
    ON data_extension_records (tenant_id, data_extension_id, created_at DESC);
