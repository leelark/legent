---
name: legent-backend-safety
description: Work on Legent backend services safely. Use for Spring Boot service changes, tenant/workspace logic, Kafka events, Flyway migrations, shared modules, campaign/delivery/tracking/audience automation, or backend production-readiness fixes.
---

# Legent Backend Safety

1. Identify owning service and package under `com.legent.<service>`.
2. Read controller, DTO, service, repository, config, event publisher/consumer, migrations, and tests for the touched path.
3. Preserve service database ownership; do not cross-read another service DB.
4. Validate tenant/workspace context and fail closed on missing or mismatched scope.
5. For Kafka, validate envelopes before side effects and keep shard-aware keys on high-volume topics.
6. Use Flyway for schema changes and never edit applied migrations.
7. Add focused Maven tests near the changed module.

## Service Invariants

- `identity-service`: preserve HTTP-only cookie auth, refresh path scoping, onboarding, SSO/federation, SCIM token and scope checks.
- `foundation-service`: preserve tenant, workspace, environment, entitlement, governance, and admin-setting ownership.
- `audience-service`: keep imports, subscribers, preferences, suppressions, segments, and audience resolution bounded and tenant-scoped.
- `content-service`: preserve sanitization, brand kits, content validation, rendering safety, test sends, and outbound URL guard.
- `campaign-service`: preserve approval lifecycle, send job idempotency, batching, retry visibility, and delivery handoff correctness.
- `delivery-service`: preserve provider health, warmup, rate control, suppressions, inbox safety, retries, and feedback handling.
- `tracking-service`: preserve signed open/click/conversion validation, outbox, Kafka publication, ClickHouse isolation, and analytics pressure isolation.
- `automation-service`: preserve workflow definition versioning, trigger idempotency, schedule correctness, node execution, and recovery.
- `deliverability-service`: preserve DNS verification, DMARC, suppressions, feedback loops, reputation, and sender-domain state.
- `platform-service`: preserve webhook retries, integration ownership, notifications, and search/import platform boundaries.

Relevant gates:

```powershell
.\mvnw.cmd -pl <module> -am test
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1
```

## Required Output

- Owning service and package.
- Tenant/workspace/security impact.
- API/event/schema impact.
- Tests added or skipped with reason.
- Memory targets updated.
