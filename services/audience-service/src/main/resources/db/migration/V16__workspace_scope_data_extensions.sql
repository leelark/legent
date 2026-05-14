-- V16: Workspace-scope data extensions and records.

ALTER TABLE data_extensions
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);

UPDATE data_extensions
SET workspace_id = COALESCE(workspace_id, 'workspace-default')
WHERE workspace_id IS NULL;

ALTER TABLE data_extensions
    ALTER COLUMN workspace_id SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM data_extensions
        WHERE deleted_at IS NULL
        GROUP BY tenant_id, workspace_id, lower(name)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot create uq_de_tenant_workspace_name_active: duplicate active data extension names exist per tenant/workspace ignoring case';
    END IF;
END $$;

ALTER TABLE data_extensions DROP CONSTRAINT IF EXISTS uq_de_tenant_name;
DROP INDEX IF EXISTS uq_de_tenant_name;

CREATE UNIQUE INDEX IF NOT EXISTS uq_de_tenant_workspace_name_active
    ON data_extensions (tenant_id, workspace_id, lower(name))
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_de_tenant_workspace_created
    ON data_extensions (tenant_id, workspace_id, created_at DESC)
    WHERE deleted_at IS NULL;

ALTER TABLE data_extension_records
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);

UPDATE data_extension_records der
SET workspace_id = de.workspace_id
FROM data_extensions de
WHERE der.data_extension_id = de.id
  AND der.workspace_id IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM data_extension_records der
        LEFT JOIN data_extensions de ON de.id = der.data_extension_id
        WHERE der.workspace_id IS NULL
           OR de.id IS NULL
           OR der.tenant_id <> de.tenant_id
           OR der.workspace_id <> de.workspace_id
    ) THEN
        RAISE EXCEPTION 'Cannot workspace-scope data_extension_records: records must match parent data_extensions tenant_id and workspace_id';
    END IF;
END $$;

ALTER TABLE data_extension_records
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_der_tenant_workspace_de_created
    ON data_extension_records (tenant_id, workspace_id, data_extension_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_der_workspace_de
    ON data_extension_records (workspace_id, data_extension_id);
