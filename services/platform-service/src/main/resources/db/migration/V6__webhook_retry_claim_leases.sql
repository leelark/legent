-- V6: Add explicit claim lease timestamp for webhook retry recovery.
-- Existing RETRYING rows can still be recovered via updated_at fallback in application code.

ALTER TABLE webhook_retries
ADD COLUMN IF NOT EXISTS claim_started_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_webhook_retries_retrying_claim
    ON webhook_retries(status, claim_started_at, updated_at)
    WHERE status = 'RETRYING';

COMMENT ON COLUMN webhook_retries.claim_started_at IS
'Timestamp when a retry worker claimed the row; stale claims are returned to PENDING by the retry scheduler.';
