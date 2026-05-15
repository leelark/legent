-- Allow idempotency rows to represent in-flight claims. Consumers set processed_at
-- only after all feedback-loop side effects complete.
ALTER TABLE deliverability_event_idempotency
    ALTER COLUMN processed_at DROP NOT NULL;
