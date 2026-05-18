# Database Map

Last updated: 2026-05-18.

Source: `docker-compose.yml`, `rg --files services | rg 'db[\\/]migration'`.

- PostgreSQL init creates one DB per service: foundation, identity, audience, content, campaign, delivery, tracking, automation, deliverability, platform.
- ClickHouse database `legent_analytics` is used by tracking.
- Flyway migrations exist under each service `src/main/resources/db/migration`; 2026-05-18 scan counted 108 SQL migrations.
- Migration versions seen in scan: foundation V1-V15, audience V1-V17, campaign V1-V14, delivery V1-V13, identity V1 and V3-V10, tracking V1 and V3-V10, content V1-V7, deliverability V1-V8, automation V1 and V3-V6, platform V1-V4.
- Do not edit historical migrations. Missing version numbers can be valid historical gaps; never renumber applied migrations.

Open risk:

- Identifier column lengths vary across services (`tenant_id`, `workspace_id`, `environment_id` parsed as 26/36/64). This can affect cross-service FK/index/DTO compatibility. Next action: choose a canonical length and add forward-only widening migrations when touching related schema.

Workspace maturity notes from 2026-05-13 DB/API audit:

- Audience workspace hardening covers subscribers/lists/segments/suppressions/imports/memberships/consent/idempotency plus data extensions and data extension records after V16. Data extension fields remain children of workspace-scoped data extensions.
- Resolved 2026-05-16, rechecked 2026-05-17: content authenticated resources are workspace-scoped in schema/repository/service/controller paths; public content reads remain intentionally public/trimmed. Legacy nullable rows still need operator-reviewed backfill before workspace-scoped visibility.
- Campaign send-job core is workspace-scoped, but approvals/checkpoints/resume/throttling are tenant/job scoped.
- Resolved 2026-05-18: new campaign send batches persist recipients in `send_batch_recipients` with tenant/workspace/job/batch sequence identity and payload JSON per recipient, while `send_batches.payload` stays metadata-only for row-backed batches.
- Delivery message/rate/warmup/safety/replay/capacity/idempotency tables are workspace-aware; SMTP providers/IP pools/routing/provider scores remain tenant-only.
- Tracking raw events/summaries are workspace-aware; some aggregate/bot-pattern tables remain global or tenant-only.
- Platform schema is tenant-only; no workspace filter found.
- Resolved 2026-05-13: every main service `application.yml` now defaults `spring.jpa.hibernate.ddl-auto` to `${SPRING_JPA_HIBERNATE_DDL_AUTO:validate}`; test resources keep intentional `create-drop`.
- Resolved 2026-05-16: automation V6 and deliverability V8 now allow nullable `processed_at` for pending idempotency claims so failed trigger/feedback side effects can retry.
