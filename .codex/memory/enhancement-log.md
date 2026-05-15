# Enhancement Log

Last updated: 2026-05-16.

- 2026-05-13: Orchestration enhancement added. No product feature behavior changed.

Candidate enhancements:

- Chunked audience resolution with cursor checkpoints. Current state: chunked/keyset resolution is implemented; manifest/checkpoint snapshot flow remains future work.
- Shard-aware Kafka keys for high-volume topics. Current state: high-volume tenant-key fallback has been reduced; service-level event contract tests remain useful.
- Send render cache/pre-render strategy. Current state: bounded render cache and oversized payload guard exist; object-storage content reference handoff remains future work.
- Production-safe config defaults for Kafka trust and JPA DDL. Current state: Kafka trust and JPA/Flyway defaults are hardened; keep validators current.
- Modular split for largest UI/backend files.

Product capability scan, 2026-05-13:

- Present: multi-tenant enterprise shell, audience/lists/imports/preferences/suppressions/segments/data extensions, content/email studio, campaign orchestration, delivery/deliverability, tracking/analytics, automation/journeys, platform/admin.
- Gaps versus enterprise marketing cloud depth: Send Flow/classifications, publication calendar, Content Builder polish, client previews, collaboration, Journey Builder entry/re-entry/goals/exits/history analytics, Automation Studio SQL/file/drop/extract/script depth, relationship graph/browser, governed field model, retention enforcement evidence, import reconciliation, bot filtering, scheduled extracts, attribution/BI dashboards, developer portal, app/package manager, API key scopes, schema registry, webhook replay UX, business-unit hierarchy UX.
- Deliverability gaps: live ISP/provider feedback feeds, blocklist feeds, BIMI, MTA-STS, TLS-RPT, warmup automation, inbox placement measurement, and real DNS production evidence. Never claim guaranteed inbox placement.

Production-readiness feature priorities:

1. Redesign high-volume sends around cursor/chunked audience snapshots, metadata-only Kafka events, shard-aware keys, idempotent checkpoints, pre-render/cache, and isolated tracking/feedback pressure.
2. Harden Kafka reliability/security with narrowed trust, explicit retry/DLQ, and event contract tests.
3. Add delivery capacity controls: provider/domain queues, adaptive throttling, warmup capacity models, Redis/sharded rate reservations, provider SLO dashboards, replay/operator workflows.
4. Produce GA evidence: synthetic smoke, high-volume import/send/tracking load tests, restore drill with RPO/RTO, canary/rollback drill, scans, SLO dashboards, alert routing.
5. Close core product parity around send flow, journey/automation depth, content previews/collaboration, and guided launch diagnostics.
6. Strengthen governance: consent ledger, retention enforcement, unsubscribe/suppression audits, privacy request workflows, immutable audit exports, tenant/workspace isolation tests.
