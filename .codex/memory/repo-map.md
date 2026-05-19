# Repo Map

Fresh baseline date: 2026-05-20.

Top-level map:
- `frontend/`: Next.js UI, authenticated workspace, public routes, API client, auth and tenant stores, Playwright tests.
- `services/identity-service/`: auth, signup/login/session, refresh, recovery, onboarding, SSO/federation, SCIM.
- `services/foundation-service/`: tenants, workspaces, environments, entitlements, governance, admin settings, public CMS, platform core.
- `services/audience-service/`: subscribers, imports, lists, data extensions, preferences, suppressions, segments, audience resolution.
- `services/content-service/`: templates, blocks, snippets, brand kits, dynamic content, landing pages, validation, rendering, test sends.
- `services/campaign-service/`: campaign lifecycle, approvals, launch orchestration, send jobs, batching, send handoff, reconciliation.
- `services/delivery-service/`: providers, SMTP/API adapters, warmup, rate control, message logs, retry, feedback.
- `services/tracking-service/`: signed events, outbox, Kafka publication, ClickHouse analytics, WebSocket analytics.
- `services/automation-service/`: workflow definitions, triggers, schedules, node execution, journey runtime.
- `services/deliverability-service/`: suppressions, sender domains, DNS verification, feedback loops, reputation, DMARC.
- `services/platform-service/`: integrations, webhooks, retries, notifications, search/import platform surfaces.
- `shared/`: common API envelopes, constants, tenant context, security, Kafka, cache, test support.
- `config/`: gateway route map and Nginx config.
- `infrastructure/`: Kubernetes, ingress, observability, external secrets, network policy, runbooks.
- `scripts/`: ops, validation, release, setup, build, and load-test automation.
- `.codex/`: autonomous organization, skills, workflows, memory, prompts, reports, state, checkpoints, templates, utilities, worktree registry.
