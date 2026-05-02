-- =============================================
-- Content Service Template Workflow Schema
-- Version: V2
-- Adds approval workflow and draft mode support
-- =============================================

-- ── Template Approval Workflow ──
CREATE TABLE IF NOT EXISTS template_approvals (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    template_id         VARCHAR(36) NOT NULL REFERENCES email_templates(id) ON DELETE CASCADE,
    version_number      INT NOT NULL,
    requested_by        VARCHAR(36) NOT NULL,
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by         VARCHAR(36),
    approved_at         TIMESTAMPTZ,
    rejection_reason    VARCHAR(1000),
    comments            TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_template_approval UNIQUE (template_id, version_number, status)
);

CREATE INDEX idx_template_approvals_tenant ON template_approvals(tenant_id);
CREATE INDEX idx_template_approvals_status ON template_approvals(tenant_id, status);
CREATE INDEX idx_template_approvals_pending ON template_approvals(template_id, status) WHERE status = 'PENDING';

-- ── Template Workflow History ──
CREATE TABLE IF NOT EXISTS template_workflow_history (
    id                  VARCHAR(36) PRIMARY KEY,
    tenant_id           VARCHAR(36) NOT NULL,
    template_id         VARCHAR(36) NOT NULL REFERENCES email_templates(id) ON DELETE CASCADE,
    version_number      INT,
    action              VARCHAR(50) NOT NULL,
    from_status         VARCHAR(20),
    to_status           VARCHAR(20),
    performed_by        VARCHAR(36),
    performed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    comments            TEXT,
    metadata            JSONB DEFAULT '{}'
);

CREATE INDEX idx_workflow_history_template ON template_workflow_history(template_id);
CREATE INDEX idx_workflow_history_action ON template_workflow_history(action);

-- ── Add approval_required flag to templates ──
ALTER TABLE email_templates ADD COLUMN IF NOT EXISTS approval_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE email_templates ADD COLUMN IF NOT EXISTS current_approver VARCHAR(36);
ALTER TABLE email_templates ADD COLUMN IF NOT EXISTS last_published_version INT;
ALTER TABLE email_templates ADD COLUMN IF NOT EXISTS last_published_at TIMESTAMPTZ;

-- ── Add draft content fields for working copies ──
ALTER TABLE email_templates ADD COLUMN IF NOT EXISTS draft_subject VARCHAR(500);
ALTER TABLE email_templates ADD COLUMN IF NOT EXISTS draft_html_content TEXT;
ALTER TABLE email_templates ADD COLUMN IF NOT EXISTS draft_text_content TEXT;

-- ── Add scheduled publishing ──
ALTER TABLE template_versions ADD COLUMN IF NOT EXISTS scheduled_publish_at TIMESTAMPTZ;
ALTER TABLE template_versions ADD COLUMN IF NOT EXISTS published_by VARCHAR(36);
