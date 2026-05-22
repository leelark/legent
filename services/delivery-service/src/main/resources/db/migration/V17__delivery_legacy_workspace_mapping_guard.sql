-- V17: Fail closed when legacy provider ownership still uses workspace-default.
-- V16 scoped delivery provider configuration by workspace but intentionally used
-- workspace-default for ambiguous legacy rows. Production promotion must review
-- and remap those rows to real workspaces before this guard can pass.

DO $$
DECLARE
    unresolved_rows integer;
BEGIN
    SELECT COUNT(*) INTO unresolved_rows
    FROM (
        SELECT id FROM smtp_providers WHERE workspace_id = 'workspace-default'
        UNION ALL
        SELECT id FROM routing_rules WHERE workspace_id = 'workspace-default'
        UNION ALL
        SELECT id FROM ip_pools WHERE workspace_id = 'workspace-default'
        UNION ALL
        SELECT id FROM provider_scores WHERE workspace_id = 'workspace-default'
    ) unresolved_delivery_provider_mapping;

    IF unresolved_rows > 0 THEN
        RAISE EXCEPTION 'Delivery legacy workspace mapping guard found % workspace-default provider ownership rows', unresolved_rows
            USING HINT = 'Review legacy delivery provider, routing, pool, and provider-score rows and map them to explicit tenant workspaces before applying this migration.';
    END IF;
END $$;
