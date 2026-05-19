---
name: legent-data-migrations
description: Work on Legent database schema, Flyway migrations, high-volume data models, indexes, tenant/workspace scoping, and persistence safety.
---

# Legent Data Migrations

1. Identify owning service schema. Do not read or write another service database directly.
2. Add new Flyway migrations; never edit historical migrations that may be applied.
3. Keep production `ddl-auto` validation-only; do not introduce update/create behavior.
4. Scope tenant/workspace data explicitly.
5. For high-volume tables, consider indexes, partitioning, retention, aggregation, and write amplification.
6. Document rollback and data migration risk.

Validation:

```powershell
.\mvnw.cmd -pl <module> -am test
rg -n "ddl-auto|SPRING_JPA_HIBERNATE_DDL_AUTO" services shared config -g "*.yml" -g "*.yaml" -g "*.properties"
```

Required output:
- owning schema,
- migration file,
- rollback notes,
- performance impact,
- tests run.
