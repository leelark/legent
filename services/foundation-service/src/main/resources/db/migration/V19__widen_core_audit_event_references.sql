-- Widen audit reference columns to match tenant-owned organization ids and
-- future principal/resource identifiers that are longer than generated ULIDs.

ALTER TABLE core_audit_events ALTER COLUMN workspace_id TYPE VARCHAR(64);
ALTER TABLE core_audit_events ALTER COLUMN environment_id TYPE VARCHAR(64);
ALTER TABLE core_audit_events ALTER COLUMN actor_id TYPE VARCHAR(64);
ALTER TABLE core_audit_events ALTER COLUMN resource_id TYPE VARCHAR(64);
ALTER TABLE core_audit_events ALTER COLUMN created_by TYPE VARCHAR(64);
