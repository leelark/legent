-- V4: Add missing deleted_at and version columns to template_approvals
-- Fixes: Schema-validation: missing column [deleted_at] in table [template_approvals]
-- The TemplateApproval entity extends TenantAwareEntity -> BaseEntity requiring deleted_at and version

ALTER TABLE template_approvals 
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
