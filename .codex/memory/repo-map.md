# Repo Map

Last updated: 2026-05-13.

- `frontend/`: Next.js App Router UI. Canonical authenticated workspace routes live under `frontend/src/app/(workspace)`; `/app/**` compatibility routes re-export/redirect. Public routes are thin wrappers around marketing/auth components. `frontend/src/lib/api-client.ts` centralizes Axios, cookie/session behavior, tenant/workspace headers, 401 redirects, and API envelope handling. Zustand stores hold auth, tenant, UI, and toast state.
- `shared/legent-common`: API envelopes, constants, ID/JSON utilities, correlation/runtime guards.
- `shared/legent-security`: tenant context, filters, JWT, RBAC, exception handling, origin guard.
- `shared/legent-kafka`: event envelope, Kafka producer/consumer helpers, DLQ helper.
- `shared/legent-cache`: tenant-aware cache helpers.
- `shared/legent-test-support`: shared test helpers.
- `services/foundation-service`: tenant/workspace/admin/config/public CMS/core platform.
- `services/identity-service`: auth, sessions, refresh tokens, SSO/federation, SCIM.
- `services/audience-service`: subscribers, lists, segments, imports, suppressions, audience resolution.
- `services/content-service`: templates, content, brand kits, rendering, landing pages, test sends.
- `services/campaign-service`: campaign lifecycle, approvals, send jobs, batching, send handoff.
- `services/delivery-service`: provider selection, SMTP/API handoff, warmup/rate control, message logs, feedback.
- `services/tracking-service`: signed open/click/conversion ingestion, outbox, ClickHouse analytics, WebSocket analytics.
- `services/automation-service`: workflows, triggers, schedules, node execution.
- `services/deliverability-service`: sender domains, DNS, reputation, DMARC, feedback loops.
- `services/platform-service`: integrations, webhooks, notifications, search/import surfaces.
- `config/`: gateway route ownership and Nginx proxy config.
- `infrastructure/kubernetes/`: base, production/global overlays, ingress, observability, network policy.
- `scripts/`: cached builds, dev, ops, release, load, setup.
- `docs/`: audits, developer notes, load testing, operations, security.
- `sdk/`: TypeScript SDK.
