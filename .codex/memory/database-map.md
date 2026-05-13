# Database Map

Last updated: 2026-05-13.

Source: `docker-compose.yml`, `rg --files services | rg 'db[\\/]migration'`.

- PostgreSQL init creates one DB per service: foundation, identity, audience, content, campaign, delivery, tracking, automation, deliverability, platform.
- ClickHouse database `legent_analytics` is used by tracking.
- Flyway migrations exist under each service `src/main/resources/db/migration`; read-only agent counted 93 SQL migrations.
- Migration counts seen in scan: foundation V1-V15, audience V1-V14, campaign V1-V13, delivery V1-V12, identity V1 and V3-V10, tracking V1 and V3-V9, content V1-V7, deliverability V1-V7, automation V1 and V3-V5, platform V1-V4.
- Do not edit historical migrations. Missing version numbers can be valid historical gaps; never renumber applied migrations.

Open risk:

- Resolved 2026-05-13: every main service `application.yml` now defaults `spring.jpa.hibernate.ddl-auto` to `${SPRING_JPA_HIBERNATE_DDL_AUTO:validate}`; test resources keep intentional `create-drop`.

Workspace maturity notes from 2026-05-13 DB/API audit:

- Audience workspace hardening covers subscribers/lists/segments/suppressions/imports/memberships/consent/idempotency, but data extension tables remain tenant-only.
- Content tables are tenant-only; no workspace migration/filter found for templates/assets/snippets/tokens/brand kits/landing pages/test sends.
- Campaign send-job core is workspace-scoped, but approvals/checkpoints/resume/throttling are tenant/job scoped.
- Delivery message/rate/warmup/safety/replay/capacity/idempotency tables are workspace-aware; SMTP providers/IP pools/routing/provider scores remain tenant-only.
- Tracking raw events/summaries are workspace-aware; some aggregate/bot-pattern tables remain global or tenant-only.
- Platform schema is tenant-only; no workspace filter found.
- Identifier column lengths vary across services (`tenant_id`, `workspace_id`, `environment_id` parsed as 26/36/64). This can affect cross-service FK/index/DTO compatibility.
