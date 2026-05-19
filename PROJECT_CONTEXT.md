# PROJECT_CONTEXT.md

Context baseline rebuilt on 2026-05-20.

## Product Vision

Legent is an AI-native enterprise email marketing and lifecycle automation platform aiming for Salesforce Marketing Cloud class capability while preserving stronger operational safety, tenant isolation, deliverability controls, and autonomous engineering discipline.

The product should support teams that manage audiences, design content, approve campaigns, automate journeys, send through managed providers, track engagement, monitor deliverability, operate global infrastructure, and improve outcomes through AI-assisted optimization.

The product must not claim guaranteed inbox placement. Deliverability is a reputation and compliance system. Legent should optimize authenticated DNS, warmup, provider capacity, suppressions, engagement-aware targeting, bounce/complaint handling, feedback loops, throttling, and observability.

## Strategic Objective

Build toward mature-platform throughput of 10 lakh email triggers in 10 hours or better when:

- Sender domains and addresses are warmed.
- SPF, DKIM, DMARC, bounce, complaint, and unsubscribe handling are verified.
- Provider capacity is approved and observable.
- Recipients are eligible after suppression, preference, and engagement checks.
- Kafka, DB, Redis, ClickHouse, and provider paths are load-tested.
- Backpressure, retries, DLQ, stuck-job recovery, and incident runbooks are proven.

## Core Modules

- Identity: signup, login, cookie sessions, refresh, logout, password recovery, invitations, workspace context switching, delegation, SSO, SCIM.
- Foundation: tenant provisioning, workspace/environment setup, enterprise governance, entitlements, admin platform state.
- Audience: subscribers, data extensions, CSV imports, preferences, unsubscribe status, segmentation, audience resolution.
- Content: email templates, snippets, blocks, brand kits, personalization tokens, dynamic content, landing pages, rendering, HTML validation, test sends.
- Campaign: lifecycle, approval, preflight, scheduling, audience selection, batch creation, send execution handoff, delivery feedback reconciliation.
- Delivery: message logging, provider selection, SMTP/API adapters, inbox safety, warmup, per-domain/provider rate control, retry, bounce/failure publication.
- Tracking: signed open/click URLs, open pixel, click redirect, conversion ingestion, event idempotency, outbox, Kafka publication, ClickHouse rollups, analytics WebSocket.
- Automation: workflow definitions, triggers, node execution, delays, scheduled journeys, workflow action publication.
- Deliverability: suppressions, domain verification, DNS validation, reputation scoring, feedback loop processing.
- Platform: integrations, webhooks, retries, import platform jobs, external system surfaces.
- Frontend workspace: dashboard, launch, audience, templates, campaigns, automation, delivery, analytics, admin, settings.
- Public frontend: marketing pages, pricing, modules, about, blog, contact, login, signup, onboarding, recovery.

## Operating Roles

- Public visitor: explores public pages, signs up, logs in, requests password recovery.
- Tenant owner/admin: creates and configures tenant, workspaces, environments, roles, identity/federation settings, governance.
- Marketing operator: manages audiences, templates, campaigns, automation, analytics.
- Audience manager: imports subscribers, deduplicates records, manages preferences and data extensions.
- Content builder: creates templates, snippets, tokens, brand kits, landing pages, and test sends.
- Campaign manager: creates campaigns, configures audience/content, requests approval, schedules or launches sends.
- Campaign approver: validates preflight, compliance, targeting, and launch readiness.
- Deliverability operator: configures domains, monitors reputation, warmup, suppressions, bounces, complaints, provider health.
- Platform engineer: configures webhooks, imports, external systems, and operational integrations.
- API user: calls APIs with tenant/workspace context.

## Critical Workflows

### Tenant Bootstrap

1. User signs up through identity service.
2. Identity creates account, tenant, membership, workspace, and environment context.
3. Identity publishes bootstrap events.
4. Foundation prepares enterprise defaults.
5. Frontend stores non-secret session metadata and uses cookie-backed auth.

### Workspace Session

1. Frontend shell calls `/auth/session`.
2. API client sends HTTP-only cookie credentials.
3. Tenant/workspace/environment headers are attached.
4. Backend filters validate context and reject mismatches.
5. Workspace UI renders operational modules.

### Campaign Launch

1. Campaign is drafted, configured, approved, and preflighted.
2. Campaign service creates a send job and requests audience resolution.
3. Audience service resolves eligible subscribers in bounded chunks.
4. Campaign service writes durable recipient/batch state and hands off send work.
5. Delivery service checks suppressions, warmup, rate limits, content/link safety, and provider health.
6. Provider adapter sends mail and publishes feedback.
7. Tracking records engagement and feeds analytics.
8. Campaign and audience services reconcile feedback and intelligence.

### Automation Journey

1. Workflow is created, validated, and published.
2. Trigger events start workflow instances.
3. Node handlers execute conditions, delays, imports, queries, sends, and actions.
4. Results feed analytics, subscriber intelligence, and optimization loops.

## Product Capability Target

Legent should cover:

- Email Studio: templates, content blocks, dynamic content, personalization, A/B testing, approvals, test sends.
- Contact Builder/Data Extensions: subscribers, attributes, custom data tables, imports, query activities, segmentation, preferences.
- Journey Builder: visual journey canvas, triggers, waits, decisions, goals, exits, versioning, simulation, live monitoring.
- Automation Studio: schedules, imports, SQL/query activities, extracts, sends, webhooks, file/object storage tasks.
- Deliverability: domain authentication, DMARC, warmup, reputation, suppressions, feedback loops, inbox safety.
- Analytics: campaign metrics, journey analytics, audience intelligence, deliverability health, ClickHouse rollups.
- Admin: tenants, workspaces, users, roles, federation, SCIM, entitlements, audit, compliance evidence.
- Enterprise: approvals, governance, environments, global operations, release evidence, incident response.
- AI differentiation: subject/body assistance, content scoring, send-time optimization, predictive segments, frequency optimization, channel recommendations, anomaly detection, root-cause assistant, safe autonomous operations.

## Constraints

- Tenant/workspace context is mandatory for protected APIs/events.
- Suppressed, unsubscribed, invalid, bounced, complaint-prone, or policy-blocked recipients must not be sent.
- New senders/domains must warm up gradually.
- Provider selection must respect health, circuit breakers, routing rules, warmup, risk, and rate limits.
- Tracking links must be signed and verified.
- Production must use External Secrets, managed backing services, Flyway, immutable image evidence, and release gates.
- Route ownership must remain synchronized across route map, Nginx, and ingress.

## Current Risks

- Public GA is blocked until production evidence exists for egress, live load, restore, monitoring, TLS/admission, registry image provenance, and security scans.
- Million-send throughput still needs target-like broker/database/provider evidence.
- Automation/Journey parity needs deeper adapters, UX, simulation, versioning, and runtime monitoring.
- Large frontend/backend files should be decomposed when touched.
- `.codex` now owns autonomous coordination and must be kept current after every meaningful work cycle.
