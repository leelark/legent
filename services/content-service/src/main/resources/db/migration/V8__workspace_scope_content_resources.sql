-- V8: Scope authenticated content resources to tenant + workspace.

ALTER TABLE email_templates ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE content_blocks ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE assets ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE template_versions ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE content_block_versions ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE content_snippets ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE brand_kits ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE landing_pages ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE template_approvals ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE dynamic_content_rules ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE template_test_send_records ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE render_validation_reports ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);

UPDATE template_versions version
SET workspace_id = template.workspace_id
FROM email_templates template
WHERE version.template_id = template.id
  AND version.workspace_id IS NULL
  AND template.workspace_id IS NOT NULL;

UPDATE content_block_versions version
SET workspace_id = block.workspace_id
FROM content_blocks block
WHERE version.block_id = block.id
  AND version.workspace_id IS NULL
  AND block.workspace_id IS NOT NULL;

UPDATE template_approvals approval
SET workspace_id = template.workspace_id
FROM email_templates template
WHERE approval.template_id = template.id
  AND approval.workspace_id IS NULL
  AND template.workspace_id IS NOT NULL;

UPDATE dynamic_content_rules rule
SET workspace_id = template.workspace_id
FROM email_templates template
WHERE rule.template_id = template.id
  AND rule.workspace_id IS NULL
  AND template.workspace_id IS NOT NULL;

UPDATE template_test_send_records record
SET workspace_id = template.workspace_id
FROM email_templates template
WHERE record.template_id = template.id
  AND record.workspace_id IS NULL
  AND template.workspace_id IS NOT NULL;

UPDATE render_validation_reports report
SET workspace_id = template.workspace_id
FROM email_templates template
WHERE report.template_id = template.id
  AND report.workspace_id IS NULL
  AND template.workspace_id IS NOT NULL;

ALTER TABLE email_templates DROP CONSTRAINT IF EXISTS uq_template_tenant_name;
ALTER TABLE email_templates DROP CONSTRAINT IF EXISTS uk_template_tenant_name;
ALTER TABLE content_blocks DROP CONSTRAINT IF EXISTS uq_block_tenant_name;
ALTER TABLE content_snippets DROP CONSTRAINT IF EXISTS uq_content_snippet_key;
ALTER TABLE brand_kits DROP CONSTRAINT IF EXISTS uq_brand_kit_name;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_template_tenant_workspace_name'
    ) THEN
        ALTER TABLE email_templates
            ADD CONSTRAINT uq_template_tenant_workspace_name UNIQUE (tenant_id, workspace_id, name);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_block_tenant_workspace_name'
    ) THEN
        ALTER TABLE content_blocks
            ADD CONSTRAINT uq_block_tenant_workspace_name UNIQUE (tenant_id, workspace_id, name);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_content_snippet_workspace_key'
    ) THEN
        ALTER TABLE content_snippets
            ADD CONSTRAINT uq_content_snippet_workspace_key UNIQUE (tenant_id, workspace_id, snippet_key);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_brand_kit_workspace_name'
    ) THEN
        ALTER TABLE brand_kits
            ADD CONSTRAINT uq_brand_kit_workspace_name UNIQUE (tenant_id, workspace_id, name);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_templates_tenant_workspace
    ON email_templates(tenant_id, workspace_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_templates_workspace_status
    ON email_templates(tenant_id, workspace_id, status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_templates_workspace_type
    ON email_templates(tenant_id, workspace_id, template_type) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_blocks_tenant_workspace
    ON content_blocks(tenant_id, workspace_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_blocks_workspace_global
    ON content_blocks(tenant_id, workspace_id, is_global) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_assets_tenant_workspace
    ON assets(tenant_id, workspace_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_assets_workspace_content_type
    ON assets(tenant_id, workspace_id, content_type) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_template_versions_workspace
    ON template_versions(template_id, tenant_id, workspace_id);
CREATE INDEX IF NOT EXISTS idx_template_versions_workspace_published
    ON template_versions(template_id, tenant_id, workspace_id, is_published);

CREATE INDEX IF NOT EXISTS idx_content_block_versions_workspace
    ON content_block_versions(block_id, tenant_id, workspace_id);

CREATE INDEX IF NOT EXISTS idx_content_snippets_tenant_workspace
    ON content_snippets(tenant_id, workspace_id) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_brand_kits_workspace_default
    ON brand_kits(tenant_id, workspace_id, is_default) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_landing_pages_tenant_workspace
    ON landing_pages(tenant_id, workspace_id) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_template_approvals_workspace_template
    ON template_approvals(tenant_id, workspace_id, template_id, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_template_approvals_workspace_status
    ON template_approvals(tenant_id, workspace_id, status);

CREATE INDEX IF NOT EXISTS idx_dynamic_rules_workspace_template
    ON dynamic_content_rules(tenant_id, workspace_id, template_id, slot_key, priority)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_test_sends_workspace_template
    ON template_test_send_records(tenant_id, workspace_id, template_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_render_reports_workspace_template
    ON render_validation_reports(tenant_id, workspace_id, template_id, created_at DESC);
