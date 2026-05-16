# Technical Debt

Last updated: 2026-05-16.

Open:

- Full-audit wave 2 report is now in `.codex/reports/full-audit-2026-05-16-wave2.yaml`; keep future audit updates additive and avoid contradicting resolved memory entries.
- Frontend API-client hardening now uses typed error normalization and touched-file ESLint passed after worker follow-up. Residual compatibility debt: helper defaults retain a `LegacyApiData` `any` alias for existing broad call sites until API wrappers are fully typed domain by domain.
- Tracking raw-event local cleanup was closed on 2026-05-16 with Postgres retention guardrails, ClickHouse workspace-lineage schema contracts, atomic Redis dedupe reservations, and outbox lease metrics. Remaining debt is target-environment ClickHouse ingest/load and TTL proof before high-volume GA.
- Infra release validation now checks observability alert render, ingress TLS references, restricted Pod Security labels, deployment hardening, and optional strict image digest pinning. Remaining debt is live Prometheus/Alertmanager proof, TLS certificate ownership proof, restricted admission proof, and real registry SBOM/signature/digest provenance evidence.
- Large frontend route/components over 700-1200 lines make UI changes risky: `PublicPageView.tsx`, workspace template editor, admin/settings consoles, campaign creation page. Admin/settings visual chrome was aligned on 2026-05-16, but the consoles remain large and should still be split by panel/domain when next touched.
- Large backend services over 600-990 lines concentrate too many responsibilities: foundation global/core platform, identity federation, campaign launch orchestration, delivery orchestration.
- Docs/scripts drift around ports and validation flags was checked on 2026-05-16 with parser, route-map, backup/restore, load-harness, and Compose-health validation; keep future docs changes paired with script validation.
- Content/platform workspace-scope migrations intentionally leave legacy rows nullable; operators need reviewed backfill/mapping before those rows become visible in workspace-scoped authenticated APIs.
- 2026-05-16 cleanup removed dependency-proven unreachable frontend tracking/content/admin/layout files, `useFeatureFlag`, old tracking frontend API/WebSocket wrappers, dead `TenantException`, and an unused delivery fallback method. Keep future deletion work dependency-driven rather than filename-driven.
- Generated `target/classes/db/migration` directories exist locally; do not modify generated outputs.
- Kafka consumer error handling debt was reduced on 2026-05-13 with shared retry/DLQ wiring and owned listener rethrow behavior. Remaining debt: weak `EventEnvelope<?>` / `EventEnvelope<Object>` payload contracts still need schema validation and service-level contract tests.
- Frontend workspace shell has high blast radius: session hydration, tenant bootstrap, preferences, redirect, shell chrome, and toasts are coupled in one layout.
- Tenant/workspace metadata can drift because layout, header, auth views, context bootstrap, auth store, and tenant store all write context. Tokens are not stored in localStorage, but non-secret context still needs consistency tests.
- Flyway version gaps exist in identity, tracking, and automation. Flyway allows gaps, but audit docs should note them to avoid future renumbering.
