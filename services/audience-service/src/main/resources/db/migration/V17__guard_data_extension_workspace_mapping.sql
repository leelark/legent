-- V17: Fail closed on legacy data-extension workspace mapping.
--
-- V16 adds workspace_id and assigns historical rows to workspace-default because the
-- old schema had no authoritative workspace owner. Production upgrades with legacy
-- data must provide an operator-reviewed mapping table before this migration runs:
--
--   public.audience_data_extension_workspace_mapping_review
--     tenant_id VARCHAR(36) NOT NULL
--     data_extension_id VARCHAR(36) NOT NULL
--     target_workspace_id VARCHAR(36) NOT NULL
--     reviewed_by VARCHAR(255) NOT NULL
--     reviewed_at TIMESTAMPTZ NOT NULL
--     review_ticket VARCHAR(255)
--
-- Clean installs have no legacy rows at this point and pass without the table.

DO $$
DECLARE
    legacy_count INTEGER;
    missing_mapping_count INTEGER;
    duplicate_mapping_count INTEGER;
    invalid_target_workspace_count INTEGER;
    duplicate_name_count INTEGER;
    remaining_legacy_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO legacy_count
    FROM data_extensions
    WHERE workspace_id = 'workspace-default';

    IF legacy_count = 0 THEN
        RETURN;
    END IF;

    IF to_regclass('public.audience_data_extension_workspace_mapping_review') IS NULL THEN
        RAISE EXCEPTION
            'Legacy data_extensions require reviewed workspace mapping before release. Create public.audience_data_extension_workspace_mapping_review and populate one reviewed target_workspace_id per tenant_id/data_extension_id before rerunning Flyway.';
    END IF;

    EXECUTE $sql$
        SELECT COUNT(*)
        FROM (
            SELECT tenant_id, data_extension_id
            FROM public.audience_data_extension_workspace_mapping_review
            GROUP BY tenant_id, data_extension_id
            HAVING COUNT(*) > 1
        ) duplicate_mappings
    $sql$
    INTO duplicate_mapping_count;

    IF duplicate_mapping_count > 0 THEN
        RAISE EXCEPTION
            'Legacy data_extension workspace mapping contains duplicate tenant_id/data_extension_id rows; review and reduce to one mapping per data extension.';
    END IF;

    EXECUTE $sql$
        SELECT COUNT(*)
        FROM data_extensions de
        LEFT JOIN public.audience_data_extension_workspace_mapping_review map
            ON map.tenant_id = de.tenant_id
           AND map.data_extension_id = de.id
        WHERE de.workspace_id = 'workspace-default'
          AND (
              map.data_extension_id IS NULL
              OR NULLIF(TRIM(map.target_workspace_id), '') IS NULL
              OR NULLIF(TRIM(map.reviewed_by), '') IS NULL
              OR map.reviewed_at IS NULL
          )
    $sql$
    INTO missing_mapping_count;

    IF missing_mapping_count > 0 THEN
        RAISE EXCEPTION
            'Legacy data_extensions require complete reviewed mappings with target_workspace_id, reviewed_by, and reviewed_at before release.';
    END IF;

    EXECUTE $sql$
        SELECT COUNT(*)
        FROM public.audience_data_extension_workspace_mapping_review map
        JOIN data_extensions de
            ON de.tenant_id = map.tenant_id
           AND de.id = map.data_extension_id
        WHERE de.workspace_id = 'workspace-default'
          AND (
              map.target_workspace_id <> TRIM(map.target_workspace_id)
              OR TRIM(map.target_workspace_id) = 'workspace-default'
          )
    $sql$
    INTO invalid_target_workspace_count;

    IF invalid_target_workspace_count > 0 THEN
        RAISE EXCEPTION
            'Legacy data_extension workspace mapping target_workspace_id must not have leading/trailing whitespace and must not be workspace-default.';
    END IF;

    EXECUTE $sql$
        SELECT COUNT(*)
        FROM (
            SELECT
                de.tenant_id,
                CASE
                    WHEN de.workspace_id = 'workspace-default' THEN TRIM(map.target_workspace_id)
                    ELSE de.workspace_id
                END AS mapped_workspace_id,
                LOWER(de.name) AS normalized_name
            FROM data_extensions de
            LEFT JOIN public.audience_data_extension_workspace_mapping_review map
                ON map.tenant_id = de.tenant_id
               AND map.data_extension_id = de.id
            WHERE de.deleted_at IS NULL
            GROUP BY de.tenant_id, mapped_workspace_id, LOWER(de.name)
            HAVING COUNT(*) > 1
        ) duplicate_names
    $sql$
    INTO duplicate_name_count;

    IF duplicate_name_count > 0 THEN
        RAISE EXCEPTION
            'Reviewed legacy data_extension workspace mapping would create duplicate active names per tenant/workspace; resolve names or mapping before release.';
    END IF;

    EXECUTE $sql$
        UPDATE data_extensions de
        SET workspace_id = TRIM(map.target_workspace_id)
        FROM public.audience_data_extension_workspace_mapping_review map
        WHERE de.workspace_id = 'workspace-default'
          AND de.tenant_id = map.tenant_id
          AND de.id = map.data_extension_id
    $sql$;

    SELECT COUNT(*)
    INTO remaining_legacy_count
    FROM data_extensions
    WHERE workspace_id = 'workspace-default';

    IF remaining_legacy_count > 0 THEN
        RAISE EXCEPTION
            'Reviewed legacy data_extension workspace mapping left workspace-default data_extensions after update.';
    END IF;

    UPDATE data_extension_records der
    SET workspace_id = de.workspace_id
    FROM data_extensions de
    WHERE der.data_extension_id = de.id
      AND der.workspace_id <> de.workspace_id;

    IF EXISTS (
        SELECT 1
        FROM data_extension_records der
        LEFT JOIN data_extensions de ON de.id = der.data_extension_id
        WHERE de.id IS NULL
           OR der.tenant_id <> de.tenant_id
           OR der.workspace_id <> de.workspace_id
    ) THEN
        RAISE EXCEPTION
            'Reviewed legacy data_extension workspace mapping left records out of sync with parent data extensions.';
    END IF;
END $$;
