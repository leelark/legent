# ARCHITECTURE.md

Repository architecture baseline rebuilt on 2026-05-20.

## System Overview

Legent is a multi-tenant enterprise email marketing, lifecycle automation, deliverability, and analytics platform. It combines a Next.js workspace UI, Nginx API gateway, Spring Boot microservices, Kafka eventing, service-owned PostgreSQL databases, Redis caching, object/search/analytics stores, Docker Compose local runtime, and Kubernetes production-style manifests.

Primary request path:

1. Browser loads the Next.js frontend.
2. Frontend API client calls the gateway under `/api/v1`.
3. Nginx routes path prefixes to owning Spring services.
4. Spring Security and tenant filters validate auth, tenant, workspace, and environment context.
5. Services persist to their own PostgreSQL databases and publish Kafka events for cross-service workflows.
6. Async consumers process audience, campaign, delivery, tracking, automation, deliverability, and platform events.
7. Tracking persists raw events and writes analytics to ClickHouse and WebSocket surfaces.

## Product Domains

- Identity: account, session, cookie auth, refresh, invitations, SSO, federation, SCIM.
- Foundation: tenant, workspace, environment, entitlements, governance, admin settings, public CMS.
- Audience: subscribers, imports, preferences, suppressions, lists, segments, data extensions, audience resolution.
- Content: templates, snippets, blocks, brand kits, personalization, dynamic content, landing pages, rendering, validation, test sends.
- Campaign: campaign lifecycle, approvals, preflight, send jobs, batch orchestration, feedback reconciliation.
- Delivery: provider selection, SMTP/API adapters, warmup, rate control, inbox safety, message logs, retry, feedback publication.
- Tracking: signed open/click/conversion ingestion, event idempotency, outbox, Kafka publication, ClickHouse analytics.
- Automation: workflow definitions, triggers, schedules, node runtime, journey execution.
- Deliverability: sender domains, DNS/authentication, suppressions, feedback loops, reputation, DMARC.
- Platform: integrations, webhooks, retries, notifications, search and import platform surfaces.
- Frontend: public site, authenticated workspace shell, operational consoles, dashboards, admin/settings surfaces.

## Service Boundary Rules

Services own their persistence. Cross-service work moves through APIs, service-to-service internal calls, or Kafka events. Do not read another service database directly.

Shared modules are allowed only for stable cross-cutting behavior:

- `legent-common`: envelopes, DTOs, common exceptions, runtime guards, signing helpers.
- `legent-security`: tenant context, JWT, auth filters, RBAC, unsafe-method origin guard.
- `legent-kafka`: Kafka config, event envelopes, publishers, contract validation, DLQ helpers.
- `legent-cache`: Redis cache helpers and tenant-scoped keys.
- `legent-test-support`: test fixtures and integration support.

## Frontend Architecture

The frontend is a Next.js App Router application in `frontend/src/app`.

- Public routes include home, features, modules, pricing, about, blog, contact, login, signup, recovery, onboarding, and landing-page slugs.
- Authenticated workspace routes live under `frontend/src/app/(workspace)`.
- `frontend/src/app/app` exists as a compatibility layer and should stay thin.
- `frontend/src/proxy.ts` owns legacy route redirects.
- `frontend/src/lib/api-client.ts` centralizes Axios credentials, request ID, tenant/workspace/environment headers, response unwrapping, and 401 handling.
- `frontend/src/lib/auth.ts` stores non-secret session metadata; tokens remain in HTTP-only cookies.
- Workspace layout hydrates session and active context before rendering operational surfaces.

Frontend risks:

- Several route/component files remain large and slow to change safely.
- Operational SaaS screens need dense, scannable workflows rather than marketing-heavy visuals.
- Route compatibility layers can drift unless tests cover redirect and workspace behavior.

## Backend Architecture

The backend is a Maven multi-module Java 21/Spring Boot monorepo.

Root modules:

- `shared/legent-common`
- `shared/legent-security`
- `shared/legent-kafka`
- `shared/legent-cache`
- `shared/legent-test-support`
- `services/audience-service`
- `services/automation-service`
- `services/campaign-service`
- `services/content-service`
- `services/deliverability-service`
- `services/delivery-service`
- `services/foundation-service`
- `services/identity-service`
- `services/platform-service`
- `services/tracking-service`

Common patterns:

- REST controllers expose `/api/v1/...`.
- Spring Security protects service routes.
- Tenant filters validate tenant/workspace/environment context.
- JPA repositories and Flyway migrations manage local persistence.
- Kafka publishers/consumers coordinate cross-service workflows.
- Event envelopes carry tenant, workspace, actor, request, correlation, idempotency, event type, and payload metadata.
- Testcontainers and focused service tests exist for high-risk paths.

Backend risks:

- Some service classes remain too large and mix orchestration, validation, persistence, and integration.
- Broader inbound event schema/version strategy is still needed.
- DB+Kafka atomicity is not universal; transactional outbox should be preferred for new critical paths.

## API And Routing

Gateway ownership is documented in `config/gateway/route-map.json` and implemented in `config/nginx/nginx.conf`. Kubernetes ingress must preserve the same routing intent.

Route groups:

- Identity: `/api/v1/auth`, `/api/v1/users`, `/api/v1/sso`, `/api/v1/scim/v2`, `/api/v1/federation`.
- Foundation: `/api/v1/configs`, `/api/v1/feature-flags`, `/api/v1/tenants`, `/api/v1/core`, `/api/v1/admin`, `/api/v1/public`, `/api/v1/health`, `/api/v1/audit-logs`, `/api/v1/compliance`, `/api/v1/performance-intelligence`, `/api/v1/global`, `/api/v1/differentiation`.
- Audience: `/api/v1/audience`, `/api/v1/subscribers`, `/api/v1/segments`, `/api/v1/imports`, `/api/v1/lists`, `/api/v1/data-extensions`, `/api/v1/preferences`, `/api/v1/suppressions`.
- Content: `/api/v1/templates`, `/api/v1/content`, `/api/v1/assets`, `/api/v1/emails`, `/api/v1/personalization-tokens`, `/api/v1/brand-kits`, `/api/v1/landing-pages`, `/api/v1/public/landing-pages`.
- Campaign: `/api/v1/campaigns`, `/api/v1/send-jobs`.
- Delivery: `/api/v1/providers`, `/api/v1/delivery`.
- Automation: `/api/v1/workflows`, `/api/v1/workflow-definitions`, `/api/v1/automation-studio`.
- Tracking: `/api/v1/tracking`, `/api/v1/analytics`, `/ws/analytics`.
- Deliverability: `/api/v1/deliverability`, `/api/v1/reputation`, `/api/v1/dmarc`.
- Platform: `/api/v1/platform`, `/api/v1/admin/webhooks`, `/api/v1/admin/search`.

The singular `/api/v1/track` prefix is an Nginx-only tombstone returning 410 and must not be reintroduced into the route map or ingress.

## Data Architecture

Local Compose creates separate PostgreSQL databases for services. Production overlays remove local backing services and expect managed infrastructure plus External Secrets.

Database rules:

- Use Flyway migrations only for schema changes.
- Never edit historical migrations that may already be applied.
- Tenant/workspace columns are isolation-critical.
- Large recipient sets must be cursor-paged or snapshot-backed.
- Per-message data needs retention, partitioning, and aggregation strategy.
- Hot rate-control state must avoid single-row contention at provider/domain scale.

## Event Architecture

Kafka is the asynchronous backbone. High-volume event keys must be shard-aware and avoid tenant-only partitioning.

High-risk topics and flows:

- Audience resolution requests and resolved chunks.
- Campaign send jobs, batches, retries, and feedback.
- Email send requested/sent/failed/bounced/complaint events.
- Tracking ingestion and analytics rollups.
- Workflow triggers and automation actions.
- Platform webhook retries and notification/search events.

Event rules:

- Validate envelope and payload before side effects or idempotency claims.
- Preserve tenant/workspace/request/correlation/idempotency metadata.
- Rethrow retryable consumer failures so retry/DLQ infrastructure can work.
- Use transactional outbox patterns for critical publish-after-DB paths where practical.

## Security Architecture

Security components:

- JWT HMAC access tokens and refresh tokens.
- HTTP-only browser cookies.
- Tenant cookie and context headers.
- Shared JWT authentication filter, tenant filter, RBAC evaluator, and unsafe-method origin/referer guard.
- Signed tracking URL verification.
- Outbound URL guard.
- HTML sanitization for email and landing-page content.
- SCIM bearer token validation inside provisioning service.
- Production External Secrets and no local placeholder secrets in production.

Open security posture:

- CSP still permits inline script/style in gateway config and should be tightened only with frontend compatibility work.
- Broader event schema/version enforcement remains a security and correctness need.
- Public route changes need explicit threat model and tests.

## High-Volume Send Target

10 lakh emails in 10 hours is roughly 27.8 accepted send attempts per second before retries and tracking volume. This target is only realistic for warmed domains/providers with authenticated DNS, suppression discipline, provider capacity approval, queue sharding, backpressure, observability, and live evidence.

Target architecture:

- Recipient snapshot table or object-backed chunks keyed by tenant, workspace, job, chunk, and version.
- Metadata-only chunk-ready events.
- Shard-aware Kafka keys by job, batch, provider, recipient domain, or computed shard.
- Separate render, reservation, send, feedback, and tracking pipelines.
- Provider/domain token reservations with Redis/sharded leases or batched DB reservations.
- Isolated tracking ingestion and ClickHouse rollups.
- KEDA/HPA from Kafka lag, provider capacity, error rate, and queue age.
- Stuck-job detection and replay tooling.

Do not bypass warmup or deliverability safety to satisfy throughput.

## Autonomous Development Architecture

The project-local AI organization lives under `.codex/`:

- `.codex/bootstrap.md`: startup and recovery entry point.
- `.codex/agents/`: role catalog, routing, coordination protocol.
- `.codex/commands/`: repeatable command runbooks.
- `.codex/workflows/`: SDLC workflows from intake through release.
- `.codex/skills/`: project-local procedural skills.
- `.codex/memory/`: durable current-state memory.
- `.codex/state/`: machine-readable team state.
- `.codex/checkpoints/`: resumable in-flight checkpoints.
- `.codex/reports/`: generated audit and execution reports.
- `.codex/schemas/`: schemas for state and checkpoints.

This organization coordinates human-like software company functions: product, architecture, implementation, QA, security, performance, operations, release, documentation, and continuous improvement.
