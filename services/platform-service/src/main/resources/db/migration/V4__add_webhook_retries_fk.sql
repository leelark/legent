-- V4: Add foreign key constraint to webhook_retries table
-- LEGENT-HIGH-007: Prevents orphaned retry records when webhooks are deleted

-- Add FK constraint with ON DELETE CASCADE to automatically clean up retries when webhook is deleted
ALTER TABLE webhook_retries
ADD CONSTRAINT fk_webhook_retries_webhook
FOREIGN KEY (webhook_id) REFERENCES webhooks(id)
ON DELETE CASCADE;

-- Add comment documenting the relationship
COMMENT ON CONSTRAINT fk_webhook_retries_webhook ON webhook_retries IS
'Cascading delete: when a webhook is deleted, all its retry records are automatically removed';
