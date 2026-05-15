-- V6: Allow automation idempotency rows to represent in-flight claims.

ALTER TABLE automation_event_idempotency
    ALTER COLUMN processed_at DROP NOT NULL;
