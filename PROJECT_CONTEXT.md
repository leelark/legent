# PROJECT_CONTEXT.md

Repository analysis date: 2026-05-13.

## Product Vision

Legent is intended to be an enterprise-grade email marketing and lifecycle automation platform, comparable in scope to Salesforce Marketing Cloud but built around an AI-native engineering workflow. The product should support multi-tenant teams that create audiences, design emails, approve campaigns, automate journeys, send through managed providers, track engagement, and improve deliverability over time.

The strategic ambition includes very high throughput, but the business must treat deliverability as a reputation system, not a guarantee. No software can truthfully guarantee that every message from a new domain and new email address lands in the inbox. The product should instead provide domain authentication, progressive warmup, suppression compliance, engagement-aware targeting, bounce/complaint handling, provider feedback, observability, and safe throttling.

## Core Business Modules

- Identity: signup, login, cookie sessions, refresh, logout, password recovery, invitations, workspace context switching, delegation, SSO and SCIM.
- Foundation: tenant provisioning, workspace/environment setup, enterprise governance, entitlements, admin platform state.
- Audience: subscribers, data extensions, CSV import, preferences, unsubscribe status, segmentation, audience resolution.
- Content: email templates, snippets, blocks, brand kits, personalization tokens, dynamic content, landing pages, rendering, HTML validation, test sends.
- Campaign: campaign lifecycle, approval, preflight checks, scheduling, audience selection, batch creation, send execution handoff, delivery feedback reconciliation.
- Delivery: message logging, provider selection, SMTP/API adapter flow, inbox safety evaluation, warmup, per-domain/provider rate control, retry, bounce and failure publication.
- Tracking: signed open/click URLs, open pixel, click redirect, conversion ingestion, event idempotency, outbox, Kafka publication, ClickHouse rollups, analytics WebSocket.
- Automation: workflow definitions, triggers, node execution, delays, scheduled journeys, workflow action publication.
- Deliverability: suppressions, domain verification, DNS validation, reputation scoring, feedback loop processing.
- Platform: integrations, webhooks, retries, import platform jobs, external system surface.
- Frontend workspace: authenticated app shell for dashboard, launch, audience, templates, campaigns, automation, delivery, analytics, admin, settings.
- Public frontend: marketing pages, pricing, modules, about, blog, contact, login, signup, onboarding, recovery.

## User Roles

- Public visitor: explores public pages, signs up, logs in, requests password recovery.
- Tenant owner/admin: creates and configures tenant, workspaces, environments, roles, identity/federation settings, platform governance.
- Marketing operator: manages audiences, segments, templates, campaigns, automation, analytics.
- Audience manager: imports subscribers, deduplicates records, manages preferences and data extensions.
- Content builder: creates templates, snippets, personalization tokens, brand kits, landing pages, and test sends.
- Campaign manager: creates campaigns, configures audience and content, requests approval, schedules or launches sends.
- Campaign approver: validates preflight, compliance, targeting, and launch readiness.
- Deliverability operator: configures sending domains, monitors reputation, warmup, suppressions, bounces, complaints, provider health.
- Platform/integration engineer: configures webhooks, imports, external systems, and operational integrations.
- API/client user: calls public/internal APIs with tenant and workspace context.

## Critical Workflows

### Tenant Bootstrap

1. User signs up through identity service.
2. Identity creates account, tenant, membership, default workspace, and environment context.
3. Identity publishes signup/bootstrap events.
4. Foundation consumes tenant bootstrap/provisioning events and prepares enterprise defaults.
5. Frontend stores non-secret session metadata and uses cookie-backed auth.

### Authenticated Workspace Session

1. Frontend app shell calls `/auth/session`.
2. API client sends credentials via HTTP-only cookies.
3. Tenant/workspace/environment headers are attached from local context.
4. Backend filters validate tenant context and reject cross-tenant mismatches.
5. Workspace UI renders Dashboard, Launch, Audience, Templates, Campaigns, Automation, Delivery, Analytics, Admin, and Settings.

### Audience Import And Segmentation

1. User uploads CSV/import file.
2. Audience service stores import content in MinIO.
3. Import processing validates rows, chunks work, and upserts subscribers.
4. Preferences, suppressions, and custom fields shape audience eligibility.
5. Segment evaluation uses rule trees and database queries to calculate membership.

### Content Creation

1. User creates templates, snippets, blocks, dynamic content, and brand kit assets.
2. Content service validates and sanitizes HTML.
3. Render flow resolves personalization and dynamic content.
4. Test sends publish email send requests through Kafka.

### Campaign Launch

1. Campaign is drafted, configured, and approved.
2. Launch orchestration checks status, approvals, locks, preflight, safety, idempotency, and workspace context.
3. Campaign service creates a send job and publishes audience resolution request.
4. Audience service resolves subscribers and publishes resolved audience data.
5. Campaign service batches recipients and publishes batch events.
6. Send execution renders message content and publishes individual email send requests.
7. Delivery service selects provider, checks safety/warmup/rate limits, sends, and emits sent/failed/bounce/retry events.
8. Campaign service reconciles delivery feedback.
9. Tracking service records opens, clicks, conversions, and analytics.

### Automation Workflow

1. Workflow definitions are created in automation service.
2. Trigger events start workflow instances.
3. Workflow engine executes node handlers such as conditions, delays, and actions.
4. Send actions publish campaign/send events.
5. Results feed analytics and subscriber intelligence.

### Deliverability And Reputation

1. Sending domains are verified by DNS.
2. Warmup state limits new sender/provider volume.
3. Inbox safety checks content, suppression, complaint risk, unsubscribe, links, and history.
4. Provider health and feedback events influence routing.
5. Reputation engine and suppressions protect future sends.

## Domain Terminology

- Tenant: top-level customer/account boundary.
- Workspace: operational workspace inside a tenant.
- Environment: workspace environment such as production or sandbox.
- Subscriber: contact record eligible for communication.
- Data extension: custom audience data table or structured subscriber extension.
- Segment: rule-based audience subset.
- Campaign: marketing send definition and lifecycle object.
- Send job: campaign launch execution record.
- Send batch: chunk of recipients for processing.
- Message ID: unique email message identifier used through delivery and tracking.
- Provider: outbound email provider or SMTP/API channel.
- Warmup: gradual sender/provider/domain volume increase for reputation protection.
- Suppression: email or recipient blocked from sends due to unsubscribe, bounce, complaint, or policy.
- Inbox safety: pre-send evaluation that blocks or warns on risky content or recipient/provider state.
- Tracking event: open, click, conversion, bounce, complaint, or delivery feedback event.
- Event envelope: Kafka wrapper carrying tenant, workspace, actor, request, correlation, idempotency, and payload data.

## Business Rules

- Tenant and workspace context is mandatory for most APIs and event handling.
- Campaign launch must pass approval, lock, idempotency, and preflight checks.
- Suppressed, unsubscribed, invalid, bounced, or complaint-prone recipients must not be sent.
- Emails must include compliance-critical elements such as unsubscribe handling where required.
- New senders/domains must warm up gradually. The current delivery code starts new sender/provider combinations at very low hourly/daily limits.
- Provider selection must respect health, circuit breakers, routing rules, warmup, risk, and rate limits.
- Tracking links must be signed and verified.
- SCIM mutating operations require valid SCIM bearer token scopes even though the route is permitted at Spring Security level.
- Production configuration must use external secrets, Flyway migrations, and managed backing services.
- Route ownership must stay synchronized between gateway route map and Nginx routing.

## Product Constraints

- Guaranteed inbox placement is impossible and must not be represented as a product promise.
- Sending 1,000,000 emails in 10 hours requires approximately 27.8 sends/second sustained, plus headroom for retries, provider throttling, and tracking events.
- Sending that volume from a new domain or new address conflicts with current warmup rules and with real-world inbox provider reputation systems.
- The current audience resolution flow publishes all resolved recipients in one event, which is not viable for million-recipient sends.
- The current default Kafka partition key is tenant ID, which can hot-spot a high-volume tenant.
- Current local Compose resources and fixed consumer concurrency are not sized for enterprise send volume.
- Frontend workspace UX exists, but several large components and marketing-heavy styling will slow enterprise workflow iteration.

## Major Pain Points Found

- One-shot audience resolution event is the largest scaling blocker.
- Delivery hot path performs multiple per-message writes and synchronous checks that need batching/sharding for million-send workloads.
- Kafka listener exception handling often logs and suppresses failures, limiting retry/DLQ behavior.
- `spring.json.trusted.packages: "*"` appears in multiple service configs.
- `spring.jpa.hibernate.ddl-auto` defaults to `update` in service config files unless overridden.
- Manual route definitions exist in both `route-map.json` and Nginx config.
- Frontend has large route/component files over 700 to 1200 lines.
- Backend has large service classes such as global enterprise, core platform, federated identity, campaign launch, and delivery orchestration services.
- Test coverage exists but is uneven. Platform, automation, identity, campaign/delivery end-to-end, load, failure, and contract testing need strengthening.
