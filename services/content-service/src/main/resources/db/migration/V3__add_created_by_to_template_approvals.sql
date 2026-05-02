-- V3: Add missing created_by column to template_approvals
-- Fixes: Schema-validation: missing column [created_by] in table [template_approvals]
-- The TemplateApproval entity extends TenantAwareEntity which extends BaseEntity requiring created_by

ALTER TABLE template_approvals ADD COLUMN IF NOT EXISTS created_by VARCHAR(26);

-- Populate existing rows with a default system user if needed
-- UPDATE template_approvals SET created_by = '01HSYSTEM00000000000000001' WHERE created_by IS NULL;
