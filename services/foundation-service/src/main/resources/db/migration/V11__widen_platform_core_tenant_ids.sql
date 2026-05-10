-- Tenant ids are UUID-sized in identity/foundation bootstrap. Platform core keeps
-- generated entity ids at ULID length, except organizations.id, which is the
-- canonical tenant id for backward compatibility.

ALTER TABLE organizations ALTER COLUMN id TYPE VARCHAR(64);

DO $$
DECLARE
    column_ref record;
BEGIN
    FOR column_ref IN
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND column_name IN ('tenant_id', 'organization_id')
          AND data_type = 'character varying'
          AND character_maximum_length < 64
    LOOP
        EXECUTE format(
            'ALTER TABLE %I ALTER COLUMN %I TYPE VARCHAR(64)',
            column_ref.table_name,
            column_ref.column_name
        );
    END LOOP;
END $$;
