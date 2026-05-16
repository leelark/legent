# Audience Data Extension Workspace Mapping

Last reviewed: 2026-05-14.

Audience data extensions were originally tenant-scoped. `V16__workspace_scope_data_extensions.sql` adds `workspace_id`, but historical production data has no authoritative workspace owner in the audience schema. Operators must not infer workspace ownership from name, tenant defaults, or campaign usage without a reviewed source of truth.

`V17__guard_data_extension_workspace_mapping.sql` fails closed when legacy `data_extensions` rows exist with `workspace_id = 'workspace-default'` unless the database already contains reviewed mapping metadata.

Reviewed `target_workspace_id` values must be non-empty, must not include leading or trailing whitespace, and must not be `workspace-default`. V17 uses `TRIM(target_workspace_id)` consistently when checking duplicate names and writing the final workspace value, then asserts that no `workspace-default` legacy data-extension rows remain.

Important migration-order caveat: `V16__workspace_scope_data_extensions.sql` assigns unmapped legacy rows to `workspace-default` and then creates the tenant/workspace/name uniqueness guard. If a legacy tenant already has active data extensions whose names differ only by case and those rows must map to different workspaces, V16 can fail before V17 has a chance to remap them. For those environments, run the pre-V16 review below against a staging clone and resolve duplicate names or obtain explicit approval before applying V16. Do not edit applied Flyway migrations in production.

Clean installs are unaffected because no legacy rows exist when V17 runs.

## Required Preflight

Before applying V17 to an environment with existing audience data extensions:

1. Export the candidate legacy rows:

```sql
SELECT
    tenant_id,
    id AS data_extension_id,
    name,
    created_at,
    updated_at,
    deleted_at
FROM data_extensions
WHERE workspace_id = 'workspace-default'
ORDER BY tenant_id, name, id;
```

2. Review each row against an authoritative operator source such as tenant onboarding records, foundation workspace records, signed migration tickets, or customer-approved migration evidence.

3. Before writing the review table, verify every proposed `target_workspace_id` against the authoritative foundation workspace source for the same tenant. Because audience and foundation run in separate databases, use one of these approved workflows:

- Export proposed mappings from the audience staging clone, import only `tenant_id`, `data_extension_id`, and `target_workspace_id` into a temporary review table in the foundation database or approved read replica, then run the query below.
- Use an approved foreign-data-wrapper/read-replica workflow that lets the foundation workspace table and the proposed mapping table be joined without copying customer data beyond the reviewed migration workspace.
- Attach a signed operator report from the foundation workspace source that proves every proposed `target_workspace_id` exists for the same tenant and is active/not deleted.

Temporary-table example for the foundation database:

```sql
CREATE TEMP TABLE tmp_audience_data_extension_workspace_mapping_review (
    tenant_id VARCHAR(36) NOT NULL,
    data_extension_id VARCHAR(36) NOT NULL,
    target_workspace_id VARCHAR(36) NOT NULL
);

-- Load reviewed proposed mappings into the temp table, then run:
SELECT map.tenant_id, map.data_extension_id, map.target_workspace_id
FROM tmp_audience_data_extension_workspace_mapping_review map
LEFT JOIN workspaces w
    ON w.tenant_id = map.tenant_id
   AND w.id = TRIM(map.target_workspace_id)
WHERE w.id IS NULL
   OR COALESCE(w.status, '') <> 'ACTIVE'
   OR w.deleted_at IS NOT NULL;
```

This query must return zero rows before V17 is allowed to run. If the foundation schema uses different status or deletion columns in the target environment, use the equivalent active-workspace predicate from the deployed foundation schema and attach that query to the change record.

4. Create and populate the mapping table before Flyway reaches V17:

```sql
CREATE TABLE IF NOT EXISTS public.audience_data_extension_workspace_mapping_review (
    tenant_id VARCHAR(36) NOT NULL,
    data_extension_id VARCHAR(36) NOT NULL,
    target_workspace_id VARCHAR(36) NOT NULL,
    reviewed_by VARCHAR(255) NOT NULL,
    reviewed_at TIMESTAMPTZ NOT NULL,
    review_ticket VARCHAR(255),
    PRIMARY KEY (tenant_id, data_extension_id)
);
```

`target_workspace_id` must be the final reviewed workspace ID. Do not pad it with whitespace and do not use `workspace-default`.

```sql
INSERT INTO public.audience_data_extension_workspace_mapping_review (
    tenant_id,
    data_extension_id,
    target_workspace_id,
    reviewed_by,
    reviewed_at,
    review_ticket
)
VALUES
    ('tenant-id', 'data-extension-id', 'workspace-id', 'operator@example.com', NOW(), 'CHANGE-1234');
```

5. Check mapping row uniqueness before release:

```sql
SELECT tenant_id, data_extension_id, COUNT(*)
FROM public.audience_data_extension_workspace_mapping_review
GROUP BY tenant_id, data_extension_id
HAVING COUNT(*) > 1;
```

This query must return zero rows.

6. Check completeness and target workspace hygiene before release:

```sql
SELECT de.tenant_id, de.id, de.name
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
      OR map.target_workspace_id <> TRIM(map.target_workspace_id)
      OR TRIM(map.target_workspace_id) = 'workspace-default'
  );
```

This query must return zero rows.

7. Check duplicate active names after reviewed remapping:

```sql
SELECT mapped.tenant_id, mapped.target_workspace_id, mapped.normalized_name, COUNT(*)
FROM (
    SELECT
        de.tenant_id,
        CASE
            WHEN de.workspace_id = 'workspace-default' THEN TRIM(map.target_workspace_id)
            ELSE de.workspace_id
        END AS target_workspace_id,
        LOWER(de.name) AS normalized_name
    FROM data_extensions de
    LEFT JOIN public.audience_data_extension_workspace_mapping_review map
        ON map.tenant_id = de.tenant_id
       AND map.data_extension_id = de.id
    WHERE de.deleted_at IS NULL
) mapped
GROUP BY mapped.tenant_id, mapped.target_workspace_id, mapped.normalized_name
HAVING COUNT(*) > 1;
```

This query must return zero rows. Resolve duplicates before release by correcting the mapping or renaming/deleting duplicate active data extensions through an approved change.

8. Pre-V16 duplicate-name risk check:

```sql
SELECT tenant_id, lower(name) AS normalized_name, COUNT(*)
FROM data_extensions
WHERE deleted_at IS NULL
GROUP BY tenant_id, lower(name)
HAVING COUNT(*) > 1;
```

If this query returns rows before V16 has been applied, V16 may stop before reviewed V17 remapping can run. Resolve the duplicate names through an approved data correction, or get explicit migration-history approval before changing versioned migration order.

## V17 Behavior

When reviewed mappings exist, V17:

- Rejects duplicate mapping rows.
- Rejects missing `target_workspace_id`, `reviewed_by`, or `reviewed_at`.
- Rejects `target_workspace_id` values with leading or trailing whitespace.
- Rejects `target_workspace_id = 'workspace-default'`.
- Rejects reviewed mappings that would create duplicate active data-extension names in a tenant/workspace.
- Updates legacy `data_extensions.workspace_id` from `TRIM(target_workspace_id)`.
- Fails if any legacy `data_extensions.workspace_id = 'workspace-default'` rows remain after the update.
- Updates `data_extension_records.workspace_id` from the parent data extension.

## Post-V17 Verification

After V17 completes in staging or production, operators must capture evidence that the migration finished cleanly before releasing dependent changes.

1. Confirm no legacy placeholder workspace remains:

```sql
SELECT COUNT(*) AS remaining_workspace_default_rows
FROM data_extensions
WHERE workspace_id = 'workspace-default';
```

Expected result: `0`.

2. Confirm data-extension records still match their parent extension scope:

```sql
SELECT COUNT(*) AS orphaned_or_scope_mismatched_records
FROM data_extension_records der
LEFT JOIN data_extensions de
    ON de.id = der.data_extension_id
WHERE de.id IS NULL
   OR der.tenant_id <> de.tenant_id
   OR der.workspace_id <> de.workspace_id;
```

Expected result: `0`.

3. Attach the pre-V17 authoritative foundation workspace verification evidence from the approved workflow above. Do not rely on a post-migration check as the first workspace-existence check.

## Flyway Defaults

Audience service Flyway defaults are production-safe:

- `baseline-on-migrate` defaults to `false`.
- `validate-on-migrate` defaults to `true`.
- `out-of-order` defaults to `false`.

The Kubernetes base ConfigMap sets the same values for deployed environments. Local or development workflows that require different bootstrap behavior must use explicit environment overrides.

Do not bypass this guard by manually editing Flyway history or historical migrations.
