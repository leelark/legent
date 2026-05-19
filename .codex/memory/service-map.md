# Service Map

Fresh baseline date: 2026-05-20.

Service responsibilities:
- `identity-service`: authentication, sessions, recovery, onboarding, SSO/federation, SCIM.
- `foundation-service`: tenants, workspaces, environments, entitlements, governance, admin settings, public CMS.
- `audience-service`: subscribers, imports, lists, data extensions, preferences, suppressions, segments, audience resolution.
- `content-service`: templates, blocks, snippets, brand kits, dynamic content, landing pages, validation, rendering, test sends.
- `campaign-service`: campaign lifecycle, approvals, launch orchestration, send jobs, batching, handoff, reconciliation.
- `delivery-service`: provider selection, SMTP/API delivery, warmup, rate control, message logs, retries, feedback.
- `tracking-service`: signed open/click/conversion ingestion, outbox, Kafka, ClickHouse, WebSocket analytics.
- `automation-service`: workflow definitions, triggers, schedules, node execution, journey runtime.
- `deliverability-service`: suppressions, sender domains, DNS verification, feedback loops, reputation, DMARC.
- `platform-service`: integrations, webhooks, retries, notifications, search/import platform surfaces.
