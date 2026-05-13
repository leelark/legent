# AGENTS.md

This is the AI operating manual for the Legent repository. It is based on repository analysis completed on 2026-05-13.

## Project Overview

Legent is an enterprise email marketing, lifecycle automation, and deliverability platform. The product combines a Next.js workspace UI with a Java/Spring microservice backend, Kafka event flows, per-service PostgreSQL databases, Redis caching, MinIO object storage, OpenSearch search, ClickHouse analytics, Docker Compose for local development, and Kubernetes manifests for production-style deployment.

The application is not a simple newsletter sender. It has tenant onboarding, workspace/environment context, audience imports and segmentation, email content creation, campaign approvals, send orchestration, provider selection, inbox safety checks, warmup/rate control, tracking ingestion, analytics, automation workflows, deliverability monitoring, platform integrations, and administrative consoles.

Important product constraint: the system must never promise guaranteed inbox placement. New domains and new sender addresses cannot safely send 10 lakh emails in 10 hours. Current code intentionally limits new sender/provider warmup to very low initial volumes. High-volume sending requires warmed domains, authenticated DNS, provider capacity, complaint control, engagement, queue partitioning, and reputation data.

## Tech Stack

- Frontend: Next.js 16.2.6, React 19, TypeScript 5.4, Tailwind CSS 3.4, Zustand, Axios, Framer Motion, GSAP, Chart.js, lucide-react, Playwright.
- Backend: Java 21, Spring Boot 3.2.5, Spring Web, Spring Security, Spring Data JPA, Spring Kafka, Flyway, MapStruct, Lombok, Micrometer, Resilience4j.
- Shared backend modules: `legent-common`, `legent-security`, `legent-kafka`, `legent-cache`, `legent-test-support`.
- Data: PostgreSQL 15 locally, Redis 7, MinIO, OpenSearch 2.13, ClickHouse.
- Messaging: Kafka with topics for identity, tenant bootstrap, audience, send, email delivery, tracking, automation, deliverability, platform imports, and workflow events.
- Local infrastructure: `docker-compose.yml`, `config/nginx/nginx.conf`, `config/gateway/route-map.json`.
- Deployment: Kubernetes base and production overlays under `infrastructure/kubernetes`.
- CI/security: GitHub Actions workflow `ci-security.yml`, Maven tests, frontend lint/build/E2E smoke, npm audit, gitleaks, Trivy, kustomize render.

## Folder Structure

- `frontend/`: Next.js app. Public marketing pages, authenticated workspace routes, API client, auth store, design system components, Playwright E2E tests.
- `services/identity-service/`: Auth, signup, session, workspace context, password recovery, SSO/federation, SCIM provisioning.
- `services/foundation-service/`: Tenant/workspace/environment bootstrap, entitlements, enterprise admin, governance, core platform settings.
- `services/audience-service/`: Subscribers, imports, data extensions, preferences, segmentation, audience resolution.
- `services/content-service/`: Templates, snippets, brand kits, dynamic content, landing pages, rendering, validation, test emails.
- `services/campaign-service/`: Campaign lifecycle, approvals, preflight, audience resolution requests, batching, send execution, feedback reconciliation.
- `services/delivery-service/`: Email send requests, provider selection, inbox safety, warmup/rate control, message logs, retry flow.
- `services/tracking-service/`: Open/click/conversion ingestion, signed tracking URLs, outbox, Kafka publishing, ClickHouse analytics, WebSocket analytics.
- `services/automation-service/`: Workflow definitions, triggers, scheduling, engine execution, node handlers.
- `services/deliverability-service/`: Suppressions, DNS/domain verification, reputation, feedback loops, deliverability metrics.
- `services/platform-service/`: Integrations, webhooks, retries, imports, platform-facing APIs.
- `shared/`: Cross-service libraries for API envelopes, tenant context, security, Kafka, caching, testing.
- `config/`: Gateway route ownership, Nginx config, Postgres init, service runtime config.
- `infrastructure/`: Kubernetes manifests, overlays, observability, external secrets, network policy, operational docs.
- `scripts/`: Build, ops, release, validation, and load-test scripts.
- `docs/`: Audits, runbooks, production guidance, load testing notes, contracts, ADRs.
- `sdk/`: SDK client code.

## Commands

Run commands from the repository root unless noted.

### Install

```powershell
.\mvnw.cmd -DskipTests install
cd frontend
npm ci
```

### Run Locally

```powershell
Copy-Item .env.example .env
.\scripts\ops\validate-env.ps1 -Path .\.env -AllowLocalDefaults
docker compose up -d --build
```

Frontend only:

```powershell
cd frontend
npm run dev
```

Backend build/run helper:

```powershell
make backend-build
make backend-run
```

### Test

```powershell
.\mvnw.cmd test
cd frontend
npm run lint
npm run test:e2e:smoke
```

Service-specific Maven example:

```powershell
.\mvnw.cmd -pl services/campaign-service -am test
```

### Build

```powershell
.\mvnw.cmd -DskipTests package
cd frontend
npm run build
```

Docker build:

```powershell
docker compose build
```

### Deploy

Validate local deploy shape:

```powershell
docker compose config
kubectl kustomize infrastructure/kubernetes/overlays/production
```

Production deployment uses the Kubernetes production overlay and external secrets. Do not deploy with local placeholder secrets.

## Coding Standards

- Follow existing module boundaries. Do not bypass a service by reaching into another service database.
- Java services use Spring Boot conventions, constructor injection, explicit DTOs, repositories, services, controllers, and Flyway migrations.
- Shared behavior belongs in `shared/*` only when it is genuinely cross-service and stable.
- Frontend code must use existing app shell, layout, API client, auth store, workspace context, and design tokens before adding new abstractions.
- API responses should use existing shared response patterns such as `ApiResponse` and paged response objects.
- Every tenant-scoped backend operation must respect `TenantContext`, `X-Tenant-Id`, `X-Workspace-Id`, and `X-Environment-Id` semantics.
- Keep changes small and traceable. Prefer focused service-level fixes over cross-repo rewrites unless redesign is explicitly requested.
- Use Flyway migrations for schema changes. Do not edit old applied migrations; add a new migration.
- Use structured APIs and parsers for JSON, YAML, SQL parameters, CSV, and HTML sanitization. Avoid ad hoc string parsing when a library or local helper exists.
- Prefer domain-specific tests around the changed module. Do not rely only on build success.

## Naming Conventions

- Backend packages stay under `com.legent.<service>`.
- Java classes use established suffixes: `Controller`, `Service`, `Repository`, `Entity`, `Dto`, `Config`, `Consumer`, `Publisher`.
- Kafka topics use constants from `AppConstants.KafkaTopics` where available.
- Tenant/workspace/environment headers use constants from shared/common modules where available.
- Frontend components use PascalCase. Hooks use `useX`. Stores use `XStore`. Route files follow Next.js App Router conventions.
- Migration files use Flyway `V<number>__description.sql` naming and live in each service `src/main/resources/db/migration`.
- Tests mirror production module names and focus on behavior.

## Security Rules

- Never read, print, commit, or transform `.env` secrets unless the user explicitly asks for a secret-audit workflow. Prefer `.env.example`.
- Never introduce hardcoded credentials, JWT secrets, API keys, provider keys, or internal service tokens.
- Preserve cookie-based auth behavior: access token cookie, refresh cookie path scoping, tenant cookie, secure flags by environment.
- Preserve tenant/workspace isolation. Missing context must fail closed except for documented public endpoints such as health and tracking pixels/clicks.
- Do not add `permitAll` endpoints unless they are intentionally public and documented.
- SCIM endpoints are Spring `permitAll` but authenticate inside `ScimProvisioningService`; do not weaken that service-level token validation.
- Unsafe browser methods rely on SameSite cookies and origin/referer guard. Do not remove those protections without replacing them with a stronger CSRF strategy.
- Do not widen Kafka deserialization trust. Replace `spring.json.trusted.packages: "*"` with narrow packages when touching service configs.
- Do not ship `spring.jpa.hibernate.ddl-auto=update` into production behavior. Production must use Flyway and validate.
- Do not loosen content sanitization, outbound URL guard, signed tracking URLs, suppression checks, unsubscribe requirements, or inbox safety checks to increase send volume.
- Do not claim or implement "guaranteed inbox" behavior. Build for compliance, reputation, monitoring, warmup, and provider feedback.

## Performance Rules

- Do not place an entire large audience in one Kafka event, one in-memory list, or one database transaction.
- High-volume send flows must stream/chunk recipients with cursor checkpoints and idempotent chunk IDs.
- Do not key high-volume Kafka messages only by tenant ID. Use job, batch, domain, provider, or shard-aware keys to spread partitions.
- Respect backpressure from provider health, warmup state, domain risk, and rate limits.
- Avoid synchronous per-recipient remote rendering in hot send loops unless bounded and cached.
- Avoid row-level database contention in rate control for hot provider/domain keys. Prefer sharded counters, leases, or batched reservations for high-volume paths.
- Do not add heavy per-message writes without a retention, partitioning, and aggregation strategy.
- Cache only when correctness and invalidation are clear. Never cache tenant-sensitive data under a key that omits tenant/workspace context.
- For frontend, split large route components and consoles into reusable modules when touched.

## Files AI Can Modify

AI may modify these when directly relevant to a task:

- `frontend/src/**`, `frontend/tests/**`, frontend config files.
- `services/*/src/main/**`, `services/*/src/test/**`.
- `shared/*/src/main/**`, `shared/*/src/test/**`.
- `config/gateway/route-map.json` and `config/nginx/nginx.conf`, but keep them synchronized and run route validation.
- `infrastructure/kubernetes/**` when deployment behavior is part of the task.
- `scripts/**` for build, ops, validation, release, or load-test automation.
- `docs/**` and root documentation files.
- `pom.xml` and `frontend/package.json` only when dependency or build changes are required.

## Files AI Must Not Modify Without Explicit User Approval

- `.env`, real secret files, private keys, certificates, local credentials, or generated secret material.
- User-created or unrelated dirty files.
- Historical Flyway migrations that may already be applied.
- Generated logs, screenshots, traces, reports, videos, and local run artifacts unless the task is artifact cleanup.
- `node_modules/`, `target/`, `.next/`, `dist/`, `build/`, coverage outputs.
- Deleted demo/documentation bundles or other unrelated changed files already present in the worktree.

## AI Behavior Rules

- Understand actual implementation before editing.
- Never create duplicate code when a local reusable component, helper, service, or shared module exists.
- Prefer reusable components and modular architecture.
- Always analyze impact before changes, especially tenant isolation, Kafka flow, migrations, auth, and send pipeline behavior.
- Never make blind fixes.
- Never suppress exceptions only to make a test pass.
- Never hide operational failures that should go to retry, DLQ, alerting, or user-visible status.
- Fix root cause, not symptoms.
- Add tests for behavior changes and failure modes.
- Validate build/test commands relevant to the change.
- Keep user changes intact. Do not revert unrelated dirty worktree changes.
- For frontend work, verify with browser/E2E when the route or layout is user-visible.
- For deliverability work, optimize for compliance, reputation, correctness, and observability before volume.
