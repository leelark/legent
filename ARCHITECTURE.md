# ARCHITECTURE.md

Repository analysis date: 2026-05-13.

## Current Architecture

Legent is a microservice application with a Next.js frontend, Nginx API gateway, Spring Boot services, Kafka eventing, per-service PostgreSQL databases, Redis caching, object/search/analytics stores, Docker Compose for local execution, and Kubernetes manifests for production-style deployment.

High-level request path:

1. Browser loads Next.js frontend.
2. Frontend API client calls gateway under `/api/v1`.
3. Nginx routes path prefixes to specific Spring services.
4. Spring Security and tenant filters validate auth and tenant/workspace context.
5. Services use their own PostgreSQL schemas/databases and publish Kafka events for cross-service workflows.
6. Async consumers process campaign, audience, delivery, tracking, automation, and deliverability events.
7. Analytics uses tracking service tables and ClickHouse rollups.

## Module Boundaries

- Frontend owns user experience, route composition, workspace shell, public pages, and browser-side context selection. It must not contain backend business rules beyond client-side validation and display state.
- Identity owns authentication, cookie sessions, refresh, user accounts, memberships, workspace context, invitations, federation, SSO, and SCIM token validation.
- Foundation owns tenant provisioning, workspaces, enterprise settings, entitlements, governance, and admin platform data.
- Audience owns subscribers, imports, preferences, segmentation, audience intelligence, and audience resolution.
- Content owns reusable email assets, templates, snippets, brand kits, personalization rendering, HTML validation, landing pages, and test send payload creation.
- Campaign owns marketing campaign lifecycle, approvals, preflight, send jobs, send batches, campaign-level send orchestration, and feedback reconciliation.
- Delivery owns outbound email execution, provider decisions, message logs, inbox safety, warmup, rate limiting, provider health, retries, and delivery events.
- Tracking owns open/click/conversion ingestion, signed URL verification, tracking event persistence, Kafka publication, ClickHouse writing, and analytics queries.
- Automation owns workflows, triggers, schedules, node execution, and journey actions.
- Deliverability owns suppressions, DNS/domain verification, reputation, feedback loops, and deliverability metrics.
- Platform owns integrations, webhooks, retries, and platform import/event surfaces.
- Shared modules own only cross-cutting contracts and infrastructure primitives.

## Frontend Architecture

The frontend is a Next.js App Router application in `frontend/src/app`.

Actual structure:

- Public routes include home, features, modules, pricing, about, blog, contact, login, signup, recovery, onboarding, and landing-page slugs.
- Authenticated workspace routes live under `frontend/src/app/(workspace)`.
- `frontend/src/app/app` mostly re-exports workspace routes as a compatibility layer.
- Middleware redirects legacy route prefixes such as `/email`, `/audience`, `/campaigns`, `/automation`, `/tracking`, `/deliverability`, `/analytics`, `/admin`, and `/settings` into `/app/...`.
- `frontend/src/lib/api-client.ts` centralizes Axios behavior, credentials, request ID, tenant/workspace/environment headers, response unwrapping, and 401 redirect.
- `frontend/src/lib/auth.ts` stores non-secret session metadata locally and relies on HTTP-only cookies for tokens.
- `frontend/src/stores/authStore.ts` and related stores hold user/session context.
- `WorkspaceLayout` hydrates `/auth/session`, active context, preferences, shell/sidebar/header, and error boundary.

Frontend technical debt:

- Several files are too large for maintainable product iteration, including `PublicPageView.tsx`, the template editor page, and enterprise admin/settings consoles.
- Public and workspace visual systems lean heavily on gradients/glass and purple variants. Operational SaaS workflows need denser, quieter, more scannable layouts.
- Compatibility routes duplicate route ownership and should be kept intentionally thin until removed.

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

Common backend patterns:

- REST controllers expose `/api/v1/...`.
- Spring Security protects service routes.
- Tenant filters load and validate tenant/workspace/environment context.
- JPA repositories and Flyway migrations manage local service persistence.
- Kafka publishers/consumers coordinate cross-service workflows.
- Event envelopes carry tenant, workspace, actor, request, correlation, idempotency, event type, and payload metadata.
- Testcontainers support exists for integration tests.

Backend technical debt:

- Several service classes exceed 500 to 900 lines and combine orchestration, persistence, validation, and integration behavior.
- Many Kafka consumers catch broad exceptions, log, and return. That hides failures from retry/DLQ infrastructure.
- Several service configs set Kafka trusted packages to `*`.
- Service configs default JPA `ddl-auto` to `update` unless overridden.

## API Architecture

Gateway ownership is documented in `config/gateway/route-map.json` and implemented in `config/nginx/nginx.conf`.

Examples:

- `/api/v1/auth`, `/api/v1/sso`, `/api/v1/scim`, `/api/v1/identity` route to identity service.
- `/api/v1/audience`, `/api/v1/subscribers`, `/api/v1/segments`, `/api/v1/imports`, `/api/v1/data-extensions` route to audience service.
- `/api/v1/campaigns`, `/api/v1/send-jobs`, `/api/v1/experiments` route to campaign service.
- `/api/v1/content`, `/api/v1/templates`, `/api/v1/email-studio`, `/api/v1/landing-pages` route to content service.
- `/api/v1/delivery`, `/api/v1/providers`, `/api/v1/message-logs`, `/api/v1/sending` route to delivery service.
- `/api/v1/tracking`, `/api/v1/analytics`, `/ws` route to tracking service.
- `/api/v1/automation`, `/api/v1/workflows`, `/api/v1/journeys` route to automation service.
- `/api/v1/deliverability`, `/api/v1/domains`, `/api/v1/reputation`, `/api/v1/suppressions` route to deliverability service.
- `/api/v1/integrations`, `/api/v1/webhooks`, `/api/v1/platform`, `/api/v1/import-platform` route to platform service.

API risks:

- Route ownership is duplicated between JSON route map and Nginx config, so drift is possible.
- Nginx rate limits are static and local-gateway focused; high-volume production needs service-aware and route-aware limits.
- CSP currently allows inline styles/scripts in places through gateway config. Tightening needs frontend compatibility work.

## Database Design

Local Compose creates separate PostgreSQL databases for the main services:

- `identity_db`
- `foundation_db`
- `audience_db`
- `campaign_db`
- `delivery_db`
- `tracking_db`
- `automation_db`
- `deliverability_db`
- `platform_db`
- `content_db`

Each service owns its migrations in `src/main/resources/db/migration`.

Current database themes:

- Strong service ownership and Flyway usage.
- Tenant/workspace columns are common and critical.
- Delivery stores message logs, provider health, replay/retry queues, idempotency, safety evaluations, rate state, warmup state, and provider decision traces.
- Tracking stores raw events and also writes to ClickHouse for analytics and rollups.
- Audience stores subscribers, preferences, imports, segments, data extensions, and memberships.

Database risks:

- Per-message delivery writes are heavy at million-send scale.
- Rate-control rows can become hot under high volume to one domain/provider.
- Segment recomputation can load large member sets and replace memberships in bulk.
- `ddl-auto=update` defaults are unsafe outside tightly controlled local/dev flows.

## Queue And Event Flow

Kafka is the main asynchronous backbone.

Important topics include:

- Identity and tenant bootstrap topics.
- Audience topics such as resolution requested/resolved and import events.
- Send topics such as audience resolution request, audience resolved, batch created, send processing, send requested.
- Email topics such as send requested, sent, failed, bounced, retry, opened, clicked.
- Tracking ingestion topics.
- Workflow and automation topics.
- Deliverability feedback topics.
- Platform import/webhook topics.

Campaign send flow today:

1. Campaign launch publishes audience resolution requested.
2. Audience service resolves recipients.
3. Audience service publishes one resolved payload containing the filtered audience.
4. Campaign service groups recipients by domain and creates batches.
5. Campaign service renders content and publishes individual email send requests.
6. Delivery service consumes send requests, evaluates safety/rate/warmup/provider, sends, and publishes feedback.
7. Tracking service ingests user engagement and publishes tracking events.
8. Campaign and audience services consume feedback/engagement for metrics and intelligence.

Queue risks:

- Audience resolution currently publishes all recipients in one event. This will not scale to 1,000,000 recipients.
- Default `EventPublisher.publish(topic, envelope)` keys by tenant ID, which can hot-partition high-volume tenants.
- Some consumers suppress exceptions instead of letting Kafka retry/DLQ.
- Consumer concurrency values are fixed in config and not tied to partition count, lag, HPA, or provider capacity.

## Security Architecture

Security components:

- JWT HMAC access tokens and refresh tokens.
- HTTP-only cookies for browser auth.
- Tenant cookie and context headers for multi-tenant routing.
- `JwtAuthenticationFilter`, `TenantFilter`, and unsafe-method origin/referer guard in shared security.
- CORS configured through environment.
- Runtime configuration guard for production placeholder/mock-provider prevention.
- Internal API token used for selected service-to-service endpoints.
- Signed tracking URL verification for opens/clicks.
- Outbound URL guard.
- Content sanitization for email and landing page HTML.
- SCIM bearer token authentication and scope checks inside provisioning service.
- External Secrets for production Kubernetes.

Security risks:

- Kafka trusted packages are too wide in service configs.
- CSRF posture depends on SameSite cookies plus origin/referer checks; requests without Origin/Referer can pass for non-browser compatibility.
- CSP permits unsafe inline behavior.
- JWT secret length is logged at startup, which is minor metadata exposure.
- Public routes must be audited whenever adding `permitAll`.

## Scaling Bottlenecks

Highest-priority bottlenecks for 10 lakh sends in 10 hours:

- Audience resolution event carries full recipient set instead of streaming chunks.
- Kafka partition key defaults to tenant ID.
- Send execution does synchronous per-recipient rendering and per-recipient Kafka publishing.
- Delivery hot path performs multiple database writes/checks per message.
- Rate/warmup state can create hot rows.
- Provider selection and safety checks are per message rather than batched where safe.
- Consumer concurrency is static and low for the stated throughput target.
- Local Compose topic partitions and resource limits are development-sized.
- Tracking, feedback, and analytics volume are not isolated from send execution pressure.
- New domain/address warmup rules block high volume by design.

## Recommended Architecture Improvements

### High-Volume Send Redesign

- Replace one-shot audience resolved payload with cursor-based chunking.
- Store send-job recipient snapshots in a durable table/object store keyed by tenant, workspace, job, chunk, and version.
- Publish chunk-ready events with metadata only, not full million-recipient payloads.
- Use shard-aware Kafka keys: job ID, chunk ID, provider, recipient domain, or calculated shard.
- Separate render, batch, provider reservation, send, feedback, and tracking pipelines.
- Add explicit backpressure between campaign and delivery based on provider capacity and warmup state.

### Delivery Scaling

- Move rate token reservation to Redis or a sharded/batched reservation service for hot paths.
- Batch safe provider/rate checks where correctness allows.
- Partition message logs and safety evaluations by tenant/date or message date.
- Add retention and rollup strategy for per-message operational data.
- Use provider-domain queues and KEDA-style scaling from Kafka lag.
- Make fallback provider selection explicit and audited.

### Reliability

- Stop swallowing listener exceptions. Route retryable failures through Kafka retry/DLQ.
- Add idempotency keys to every workflow boundary.
- Add stuck-job detection for send jobs, batches, imports, automation instances, and webhook retries.
- Add contract tests for event envelopes and topic payloads.

### Security

- Narrow Kafka trusted packages.
- Make CSRF expectations explicit and tested.
- Tighten CSP by removing unsafe inline dependencies.
- Remove JWT secret metadata logging.
- Keep production secret and mock-provider guards in release gates.

### Frontend

- Split large pages/consoles into domain components and hooks.
- Reduce public marketing styling inside workspace routes.
- Improve dense operational views for campaign monitoring, send queue status, provider health, and deliverability incidents.
- Keep `/app` compatibility route tree thin until fully migrated.

### Operations

- Add load-test gates around send pipeline and tracking ingestion.
- Add capacity models for provider/domain warmup.
- Add production runbooks for Kafka lag, provider failure, delivery incident, tracking lag, ClickHouse lag, and stuck sends.
- Add Terraform/Helm or equivalent environment provisioning if infrastructure is meant to be repeatable beyond Kubernetes manifests.
