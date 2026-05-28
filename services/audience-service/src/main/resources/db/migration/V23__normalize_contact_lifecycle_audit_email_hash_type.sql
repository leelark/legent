-- Align the email hash column with the Hibernate String mapping used by ContactLifecycleAudit.
ALTER TABLE contact_lifecycle_audit
    ALTER COLUMN email_sha256 TYPE VARCHAR(64);
