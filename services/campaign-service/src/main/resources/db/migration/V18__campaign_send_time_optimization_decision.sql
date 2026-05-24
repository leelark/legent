-- V18: Campaign-owned approved send-time optimization decision evidence.

ALTER TABLE campaigns
    ADD COLUMN IF NOT EXISTS send_time_optimization_policy_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS send_time_optimization_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS send_time_optimization_run_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS send_time_optimization_snapshot_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS send_time_optimization_original_scheduled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS send_time_optimization_recommended_scheduled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS send_time_optimization_timezone VARCHAR(64),
    ADD COLUMN IF NOT EXISTS send_time_optimization_confidence_band VARCHAR(32),
    ADD COLUMN IF NOT EXISTS send_time_optimization_fallback_mode VARCHAR(64),
    ADD COLUMN IF NOT EXISTS send_time_optimization_blocked_reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS send_time_optimization_data_quality_reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS send_time_optimization_reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS send_time_optimization_approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS send_time_optimization_rollback_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS send_time_optimization_approved BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS send_time_optimization_approval_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS send_time_optimization_approved_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS send_time_optimization_approved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS send_time_optimization_rollback_snapshot_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS send_time_optimization_quiet_hours_gate_passed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS send_time_optimization_approval_gate_passed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS send_time_optimization_suppression_gate_passed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS send_time_optimization_warmup_gate_passed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS send_time_optimization_rate_limit_gate_passed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS send_time_optimization_provider_capacity_gate_passed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS send_time_optimization_deliverability_gate_passed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_campaign_send_time_optimization_run
    ON campaigns (tenant_id, workspace_id, send_time_optimization_run_id)
    WHERE send_time_optimization_run_id IS NOT NULL
      AND deleted_at IS NULL;
