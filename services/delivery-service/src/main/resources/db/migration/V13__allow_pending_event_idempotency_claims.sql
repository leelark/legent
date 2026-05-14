-- Allow idempotency rows to represent in-flight claims. Consumers set processed_at
-- only after side effects complete and delete unprocessed claims on failure.
ALTER TABLE delivery_event_idempotency
    ALTER COLUMN processed_at DROP NOT NULL;
