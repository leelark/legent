# AGENTS.md

AI operating manual for the Legent repository. Last reviewed on 2026-05-13.

## Mission

Legent is an enterprise email marketing, lifecycle automation, deliverability, and analytics platform. Work in this repository must protect tenant isolation, deliverability compliance, campaign correctness, operational safety, and production reliability before feature velocity.

Legent must never claim guaranteed inbox placement. New sender domains and new sender addresses cannot safely send 10 lakh emails in 10 hours. High-volume sending requires authenticated DNS, warmed sender reputation, provider capacity, suppression discipline, engagement signals, queue sharding, rate control, and observability.

## Current Stack

- Frontend: Next.js 16.2.6, React 19, TypeScript 5.4, Tailwind CSS 3.4, Zustand, Axios, Framer Motion, GSAP, Chart.js, lucide-react, Playwright.
- Backend: Java 21, Spring Boot 3.2.5, Spring Web, Spring Security, Spring Data JPA, Spring Kafka, Flyway, MapStruct, Lombok, Micrometer, Resilience4j.
- Shared backend modules: `legent-common`, `legent-security`, `legent-kafka`, `legent-cache`, `legent-test-support`.
- Data and infra: PostgreSQL 15, Redis 7, Kafka/Zookeeper, MinIO, OpenSearch, ClickHouse, MailHog, Nginx.
- Local runtime: `docker-compose.yml`, `docker-compose.override.yml`, `config/nginx/nginx.conf`, `config/gateway/route-map.json`.
- Deployment: Kubernetes base and overlays under `infrastructure/kubernetes`.
- CI/security: `.github/workflows/ci-security.yml`, Maven tests, frontend lint/build/E2E smoke, npm audit, gitleaks, Trivy, Compose config smoke, Kustomize render.

## Repository Map

- `frontend/`: Next.js App Router UI, public marketing routes, authenticated workspace routes, API client, auth/context stores, UI components, Playwright tests.
- `services/identity-service/`: login/signup/session, cookie auth, refresh tokens, password recovery, onboarding, SSO/federation, SCIM.
- `services/foundation-service/`: tenants, workspaces, environments, entitlements, governance, admin settings, public CMS, platform core.
- `services/audience-service/`: subscribers, imports, lists, data extensions, preferences, suppressions, segments, audience resolution.
- `services/content-service/`: templates, content blocks, snippets, brand kits, dynamic content, landing pages, validation, rendering, test sends.
- `services/campaign-service/`: campaign lifecycle, approvals, launch orchestration, send jobs, batching, send handoff, feedback reconciliation.
- `services/delivery-service/`: provider selection, SMTP/API adapters, inbox safety, warmup, rate control, message logs, retry and delivery feedback.
- `services/tracking-service/`: signed open/click/conversion ingestion, outbox, Kafka publication, ClickHouse analytics, WebSocket analytics.
- `services/automation-service/`: workflow definitions, triggers, schedules, node execution, journey runtime.
- `services/deliverability-service/`: suppressions, sender domains, DNS verification, feedback loops, reputation, DMARC.
- `services/platform-service/`: integrations, webhooks, retries, notifications, search/import platform surfaces.
- `shared/`: cross-service API envelopes, constants, tenant context, security filters, Kafka helpers, cache helpers, test support.
- `config/`: gateway route ownership, Nginx proxy config, local runtime config.
- `infrastructure/`: Kubernetes manifests, overlays, ingress, observability, external secrets, network policy, runbooks.
- `scripts/`: ops, release, validation, build, setup, cached build, load test automation.
- `docs/`: audits, ADRs, runbooks, load testing, contracts, production guidance.
- `sdk/`: client SDK code.

## Persistent Memory

Maintain `.codex/memory/` as durable project memory. Update it when architecture, risk, root cause, fixes, performance limits, security posture, debt, release notes, or operating decisions change.

Required memory files:

- `architecture-memory.md`
- `repo-map.md`
- `service-dependencies.md`
- `bug-history.md`
- `root-cause-history.md`
- `failed-fixes.md`
- `successful-fixes.md`
- `performance-bottlenecks.md`
- `security-findings.md`
- `technical-debt.md`
- `design-decisions.md`
- `release-history.md`

Memory rules:

- Record facts with date, source file or command, impact, and next action where useful.
- Keep memory concise but durable. Prefer append/update over scattered notes.
- For open bugs, risks, debt, and security findings, include status, source file or command, impact, and next action.
- When an item is fixed, mark the original memory entry resolved or rewrite it so current state and historical state are not contradictory.
- Keep detailed risk narratives in their owning memory file and use short cross-references elsewhere to avoid drift.
- `.codex/memory` is part of durable project memory; track it with `AGENTS.md` unless the user explicitly makes it local-only.
- Do not store secrets, `.env` values, private keys, credentials, raw tokens, or sensitive customer data.
- After each meaningful fix, update `successful-fixes.md`, `root-cause-history.md`, and `release-history.md` as applicable.
- After failed attempts, update `failed-fixes.md` with cause and avoid repeating the same approach.

## Operating Loop

For every non-trivial task:

1. Read actual implementation before editing.
2. Check `git status --short --branch` and preserve unrelated user changes.
3. Identify service/module ownership, tenant/workspace impact, event flow impact, schema impact, and tests.
4. Make the smallest coherent change that fixes the root cause.
5. Add or update focused tests for behavior and failure modes.
6. Run relevant validation commands.
7. Update project memory when new facts, risks, or fixes were discovered.
8. Summarize changed files, validation run, residual risk, and next action.

Do not make blind fixes, suppress errors to pass tests, hide operational failures, bypass service boundaries, or weaken safety controls.

## Agent Delegation

Use subagents automatically for independent ready work with disjoint ownership.
Explicit user prompting is not required after orchestration setup.
If independent tasks >= 2, single-agent execution is forbidden. Keep delegated work bounded.

- Assign clear ownership by file/module/responsibility.
- Use disjoint write scopes for parallel workers.
- Instruct agents not to revert other workers or user changes.
- Prefer read-only intelligence agents before broad refactors.
- Do not let agents commit, push, or alter secrets unless the user explicitly asked and quality gates pass.
- Merge agent findings through this operating manual and `.codex/memory/`.

## Commands

Run from repository root unless noted.

Install:

```powershell
.\mvnw.cmd -DskipTests install
cd frontend
npm ci
```

Environment and local stack:

```powershell
Copy-Item .env.example .env
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-env.ps1 -EnvFile .env -AllowPlaceholders
docker compose up -d --build
```

Frontend only:

```powershell
cd frontend
npm run dev
```

Backend helpers:

```powershell
make backend-build
make backend-run
```

Tests:

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

Build:

```powershell
.\mvnw.cmd -DskipTests package
cd frontend
npm run build
docker compose build
```

Validation:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1
docker compose config --quiet
kubectl kustomize infrastructure/kubernetes/overlays/production
```

Release gate:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1
```

Production deployment uses Kubernetes production overlays and External Secrets. Never deploy with local placeholder secrets.

## Architecture Rules

- Follow service ownership. Do not reach into another service database.
- Backend packages stay under `com.legent.<service>`.
- Java services use Spring Boot conventions: controllers, DTOs, services, repositories, entities, mappers, config, consumers, publishers.
- Shared behavior belongs in `shared/*` only when it is genuinely cross-service and stable.
- API responses should use existing `ApiResponse`, `PagedResponse`, and shared envelope patterns.
- Tenant-scoped operations must respect `TenantContext`, `X-Tenant-Id`, `X-Workspace-Id`, and `X-Environment-Id`.
- Kafka topics and tenant/workspace headers should use constants from shared modules where available.
- Route ownership must stay synchronized across `config/gateway/route-map.json`, `config/nginx/nginx.conf`, and Kubernetes ingress. Run route validation when touched.
- Use Flyway migrations for schema changes. Never edit historical migrations that may already be applied.
- Use structured APIs/parsers for JSON, YAML, SQL parameters, CSV, and HTML sanitization.
- Cache only when invalidation and tenant/workspace key scope are explicit.

## Security Rules

- Never read, print, transform, or commit `.env` secrets unless the user explicitly asks for a secret-audit workflow. Prefer `.env.example`.
- Never introduce hardcoded credentials, JWT secrets, provider keys, private keys, internal service tokens, or generated secrets.
- Preserve cookie auth: HTTP-only access cookie, refresh cookie path scoping, tenant cookie, secure flags by environment, SameSite behavior.
- Preserve unsafe-method origin/referer guard unless replacing it with a stronger CSRF strategy.
- Missing tenant/workspace context must fail closed except documented public endpoints such as health and signed tracking open/click routes.
- Do not add `permitAll` endpoints unless intentionally public, documented, and tested.
- SCIM routes may be Spring `permitAll`, but SCIM bearer token and scope validation must remain inside `ScimProvisioningService`.
- Do not widen Kafka deserialization trust. When touching service configs, replace `spring.json.trusted.packages: "*"` with narrow packages.
- Do not ship `spring.jpa.hibernate.ddl-auto=update` into production behavior. Production must use Flyway and validate.
- Do not weaken HTML sanitization, outbound URL guard, signed tracking URLs, suppression checks, unsubscribe requirements, warmup, rate controls, or inbox safety checks.

## Performance Rules

- Do not place a large audience in one Kafka event, one in-memory list, or one database transaction.
- High-volume sends must stream/chunk recipients with cursor checkpoints and idempotent chunk IDs.
- Do not key high-volume Kafka messages only by tenant ID. Use job, batch, provider, domain, or shard-aware keys.
- Respect provider health, warmup state, domain risk, suppressions, and rate limits.
- Avoid synchronous per-recipient remote rendering in hot send loops unless bounded, cached, and measured.
- Avoid row-level contention for hot provider/domain rate keys. Prefer sharded counters, Redis token reservations, leases, or batched reservations.
- Do not add heavy per-message writes without retention, partitioning, and aggregation strategy.
- Keep tracking/analytics ingestion isolated from send execution pressure.
- New sender/domain warmup safety is product behavior, not a bug to bypass.

## Frontend Rules

- Use existing app shell, workspace layout, API client, auth store, tenant store, and design tokens before adding new abstractions.
- Authenticated app routes live under `frontend/src/app/(workspace)`. `/app` compatibility routes should stay thin.
- User-visible route/layout changes require browser or Playwright verification when feasible.
- Operational SaaS surfaces should be dense, quiet, scannable, and workflow-focused.
- Split large route components/consoles into domain components/hooks when touched.
- Do not store access/refresh tokens in browser storage. Use HTTP-only cookies and non-secret session metadata only.

## Testing Rules

- Prefer domain-specific tests near the changed module.
- Backend behavior changes should have unit/service tests and, when persistence/event boundaries are involved, integration or contract tests.
- Kafka changes need event envelope/topic tests or consumer tests.
- Tenant/workspace logic needs isolation and missing-context tests.
- Frontend visible changes need lint plus Playwright/browser verification where practical.
- Load/performance-sensitive send, tracking, import, and webhook changes need bounded performance validation or documented risk.

## Quality Gates

Do not push if any relevant gate fails:

- Backend build/tests fail.
- Frontend lint/build/E2E smoke fails.
- Runtime/container health fails.
- Route map, Nginx, or ingress drift exists.
- Logs show critical startup/runtime exceptions.
- Security scans fail or secrets are exposed.
- Tenant/workspace isolation is weakened.
- Kafka event correctness, idempotency, retry, or DLQ behavior is weakened.
- Deliverability compliance, suppression, warmup, signed tracking, or inbox safety is weakened.
- Performance regressions are found on high-volume paths.

Commit and push only when explicitly requested and after relevant gates pass. If gates cannot run, say exactly what was not run and why.

## Files AI May Modify

When directly relevant:

- `frontend/src/**`, `frontend/tests/**`, frontend config files.
- `services/*/src/main/**`, `services/*/src/test/**`.
- `shared/*/src/main/**`, `shared/*/src/test/**`.
- `config/gateway/route-map.json`, `config/nginx/nginx.conf`, and ingress, but keep them synchronized.
- `infrastructure/kubernetes/**` for deployment behavior.
- `scripts/**` for build, ops, validation, release, or load-test automation.
- `docs/**`, root documentation, and `.codex/memory/**`.
- `pom.xml`, module `pom.xml`, `frontend/package.json`, and lockfiles only when dependency/build changes require it.

## Files AI Must Not Modify Without Explicit Approval

- `.env`, real secret files, private keys, certificates, local credentials, generated secret material.
- User-created or unrelated dirty files.
- Historical Flyway migrations that may already be applied.
- Generated logs, screenshots, traces, reports, videos, local run artifacts, `target/`, `.next/`, `dist/`, `build/`, coverage, `node_modules/`.
- Deleted demo/documentation bundles or unrelated changed files already present in the worktree.

## Known Current Risks To Check Before Related Work

- Audience resolution currently emits full resolved subscriber payloads.
- Default event publishing keys many events by tenant ID.
- Several service configs still contain `spring.json.trusted.packages: "*"`.
- Service configs default `SPRING_JPA_HIBERNATE_DDL_AUTO` to `update` unless environment overrides.
- Some Kafka consumers log and return on failure, bypassing retry/DLQ.
- Several backend services and frontend pages are very large and should be split when touched.
- Some docs/scripts have drifted around local ports and validation flags. Verify commands against scripts before relying on docs.

## Autonomous Orchestration

- Durable orchestration lives under `.codex/`: `bootstrap.md`, `commands/`, `memory/`, `reports/`, `checkpoints/`, and `worktrees/`.
- Start a resumed engineering session from `.codex/bootstrap.md`, then read `.codex/memory/active-work-items.md`, `.codex/memory/blocked-items.md`, and `.codex/memory/unresolved-risks.md`.
- Keep `.codex/commands/start-org.md`, `full-audit.md`, `fix-runtime.md`, `performance-pass.md`, `security-pass.md`, and `release-pass.md` aligned with real repo commands.
- Memory maps must cite source files or commands and dates. Do not store secrets, raw logs with credentials, customer data, or `.env` values.
- When using subagents, keep ownership disjoint, record agent state in `.codex/memory/active-work-items.md`, and merge final findings into the owning memory file.
- Prioritize production-readiness work with this score: `(ProductionReadinessImpact * 5) + (SecurityRisk * 4) + (UserImpact * 3) + (PerformanceImpact * 2) + TechnicalDebtImpact`.
