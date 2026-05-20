# AGENTS.md

AI operating manual for the Legent repository. Rebuilt for the autonomous development organization on 2026-05-20.

## Mission

Legent is an enterprise email marketing, lifecycle automation, deliverability, and analytics platform. Every change must protect tenant isolation, campaign correctness, deliverability compliance, operational safety, production reliability, and long-term maintainability before feature velocity.

Legent must never promise guaranteed inbox placement. A platform can optimize authentication, warmup, targeting, suppression, rate control, observability, and provider feedback; it cannot safely force new domains or new sender addresses to send 10 lakh emails in 10 hours. Treat the 10 lakh in 10 hours target as a mature, warmed, provider-approved throughput objective with live evidence requirements.

## Current Stack

- Frontend: Next.js 16.2.6, React 19, TypeScript 5.4, Tailwind CSS 3.4, Zustand, Axios, Framer Motion, GSAP, Chart.js, lucide-react, Playwright.
- Backend: Java 21, Spring Boot 3.2.5, Spring Web, Spring Security, Spring Data JPA, Spring Kafka, Flyway, MapStruct, Lombok, Micrometer, Resilience4j.
- Shared modules: `legent-common`, `legent-security`, `legent-kafka`, `legent-cache`, `legent-test-support`.
- Data and runtime: PostgreSQL 15, Redis 7, Kafka/Zookeeper, MinIO, OpenSearch, ClickHouse, MailHog, Nginx.
- Deployment: Docker Compose locally; Kubernetes base, production, global, ingress, and observability manifests under `infrastructure/kubernetes`.
- CI/security: `.github/workflows/ci-security.yml`, Maven, frontend lint/build/E2E, npm audit, gitleaks, Trivy, Compose config, Kustomize render, and ops validation scripts.

## Repository Map

- `frontend/`: Next.js App Router UI, public pages, authenticated workspace routes, API client, auth/context stores, UI components, Playwright tests.
- `services/identity-service/`: login/signup/session, cookie auth, refresh tokens, password recovery, onboarding, SSO/federation, SCIM.
- `services/foundation-service/`: tenants, workspaces, environments, entitlements, governance, admin settings, public CMS, platform core.
- `services/audience-service/`: subscribers, imports, lists, data extensions, preferences, suppressions, segments, audience resolution.
- `services/content-service/`: templates, content blocks, snippets, brand kits, dynamic content, landing pages, validation, rendering, test sends.
- `services/campaign-service/`: campaign lifecycle, approvals, launch orchestration, send jobs, batching, send handoff, feedback reconciliation.
- `services/delivery-service/`: provider selection, SMTP/API adapters, inbox safety, warmup, rate control, message logs, retry, delivery feedback.
- `services/tracking-service/`: signed open/click/conversion ingestion, outbox, Kafka publication, ClickHouse analytics, WebSocket analytics.
- `services/automation-service/`: workflow definitions, triggers, schedules, node execution, journey runtime.
- `services/deliverability-service/`: suppressions, sender domains, DNS verification, feedback loops, reputation, DMARC.
- `services/platform-service/`: integrations, webhooks, retries, notifications, search/import platform surfaces.
- `shared/`: cross-service envelopes, constants, tenant context, security filters, Kafka helpers, cache helpers, test support.
- `config/`: gateway route ownership and Nginx proxy config.
- `infrastructure/`: Kubernetes manifests, overlays, ingress, observability, external secrets, network policy, runbooks.
- `scripts/`: ops and release validation utilities.
- `docs/`: audits, operations, load testing, security, architecture, and release evidence notes.
- `.codex/`: durable autonomous organization, memory, commands, skills, prompts, workflows, checkpoints, reports, schemas, and state.
- `.ai/`: legacy local skill notes. Treat as deprecated unless explicitly migrating content into `.codex`.

## Autonomous Organization

Use `.codex/bootstrap.md` as the entry point. It defines the executive loop, agent catalog, routing matrix, memory contract, state files, checkpoints, and recovery flow.

Agent lanes are real ownership boundaries:

- Executive: CTO, Program Manager, Product Manager, Project Manager.
- Architecture: Principal Architect, System Designer, API Architect, Data Architect.
- Product: Requirements Analyst, Salesforce Parity Researcher, UX Strategist.
- Engineering: Backend service owners, Frontend Owner, Database Engineer, Integration Engineer.
- Reliability: Security Engineer, Performance Engineer, DevOps/Platform Engineer, SRE/Monitoring Engineer.
- Quality: Test Architect, QA Engineer, Release Manager, Documentation Engineer.
- Maintenance: Refactoring Engineer, Bugfix Engineer, Technical Debt Steward.

When parallel work is requested or already authorized, keep up to 6 active subagents assigned to independent scopes with disjoint write ownership. Record each lane in `.codex/state/team-state.json` or `.codex/memory/active-work-items.md`. Do not duplicate work, and do not let subagents commit, push, alter secrets, or revert unrelated user changes.

## Operating Loop

For every non-trivial task:

1. Read actual implementation before editing.
2. Run `git status --short --branch`; preserve unrelated user changes.
3. Read `.codex/bootstrap.md`, `.codex/state/team-state.json`, `.codex/memory/active-work-items.md`, `.codex/memory/blocked-items.md`, and `.codex/memory/unresolved-risks.md`.
4. Identify module ownership, tenant/workspace impact, event flow impact, schema impact, security impact, performance impact, and tests.
5. If independent tasks exist and delegation is authorized, assign disjoint agents before editing.
6. Make the smallest coherent change that fixes root cause.
7. Add or update focused tests for behavior and failure modes.
8. Run relevant validation commands from `.codex/workflows/validation-gates.md`.
9. Update memory after meaningful fixes, failures, risks, decisions, debt, release notes, or operating changes.
10. Summarize changed files, validation, residual risk, and next action.

Do not make blind fixes, suppress errors to pass tests, hide operational failures, bypass service boundaries, or weaken safety controls.

## Persistent Memory

Maintain `.codex/memory/` as durable project memory. Required memory files:

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

- Record facts with date, source file or command, impact, status, and next action.
- Keep current-state memory concise. Move long completed narratives to reports or archive summaries.
- For open bugs, risks, debt, and security findings, include owner lane, status, source, impact, and next action.
- When fixed, update the original entry so current state and historical state do not contradict.
- Do not store secrets, `.env` values, private keys, credentials, raw tokens, or customer data.
- After meaningful fixes, update `successful-fixes.md`, `root-cause-history.md`, and `release-history.md` when applicable.
- After failed attempts, update `failed-fixes.md` with cause and avoidance guidance.

## Commands

Run from repository root unless noted.

Install:

```powershell
.\mvnw.cmd -DskipTests install
cd frontend
npm ci
```

Frontend:

```powershell
cd frontend
npm run dev
npm run lint
npm run build:ci
npm run test:e2e:smoke
```

Backend:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -pl services/campaign-service -am test
```

Runtime:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-env.ps1 -EnvFile .env.example -AllowPlaceholders
docker compose config --quiet
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-compose-health.ps1
```

Validation:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-repo-artifact-hygiene.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-production-overlay.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\test-release-evidence-validators.ps1
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1 -LocalOnly -SkipBackend -SkipFrontend -SkipCompose -SkipKustomize
```

Release gates must not be weakened. If a gate cannot run, record exactly why.

## Architecture Rules

- Follow service ownership. Do not reach into another service database.
- Backend packages stay under `com.legent.<service>`.
- Use existing `ApiResponse`, `PagedResponse`, envelopes, tenant context, security filters, cache helpers, Kafka helpers, and service conventions.
- Tenant-scoped operations must respect `TenantContext`, `X-Tenant-Id`, `X-Workspace-Id`, and `X-Environment-Id`.
- Kafka topics and headers must use shared constants where available. High-volume topics need job, batch, provider, domain, or shard-aware keys.
- Route ownership must stay synchronized across `config/gateway/route-map.json`, `config/nginx/nginx.conf`, and Kubernetes ingress.
- Use Flyway migrations for schema changes. Never edit historical migrations that may already be applied.
- Use structured parsers for JSON, YAML, SQL parameters, CSV, HTML sanitization, and Kubernetes manifests.
- Cache only when invalidation and tenant/workspace key scope are explicit.

## Security Rules

- Never read, print, transform, or commit `.env` secrets unless the user explicitly asks for a secret-audit workflow. Prefer `.env.example`.
- Never introduce hardcoded credentials, JWT secrets, provider keys, private keys, internal tokens, or generated secrets.
- Preserve HTTP-only cookie auth, refresh cookie path scoping, tenant cookie behavior, secure flags by environment, SameSite expectations, and unsafe-method origin/referer guard.
- Missing tenant/workspace context must fail closed except documented public endpoints such as health and signed tracking routes.
- Do not add `permitAll` endpoints unless intentionally public, documented, and tested.
- SCIM routes may be Spring `permitAll`, but SCIM bearer token and scope validation must stay inside `ScimProvisioningService`.
- Do not widen Kafka deserialization trust.
- Do not ship production `spring.jpa.hibernate.ddl-auto=update`.
- Do not weaken HTML sanitization, outbound URL guard, signed tracking URLs, suppression checks, unsubscribe handling, warmup, rate controls, or inbox safety checks.

## Performance Rules

- Do not place a large audience in one Kafka event, in-memory list, JSON payload, or database transaction.
- High-volume sends must stream or chunk recipients with cursor checkpoints and idempotent chunk IDs.
- Keep provider health, warmup state, domain risk, suppressions, and rate limits in the send path.
- Avoid synchronous per-recipient remote rendering in hot loops unless bounded, cached, and measured.
- Avoid row-level contention for hot provider/domain rate keys. Prefer sharded counters, Redis token reservations, leases, or batched reservations.
- Do not add heavy per-message writes without retention, partitioning, and aggregation strategy.
- Keep tracking and analytics ingestion isolated from send execution pressure.

## Frontend Rules

- Use existing app shell, workspace layout, API client, auth store, tenant store, and design tokens before adding abstractions.
- Authenticated app routes live under `frontend/src/app/(workspace)`. `/app` compatibility routes should stay thin.
- Operational SaaS surfaces should be dense, quiet, scannable, and workflow-focused.
- Visible route or layout changes need browser or Playwright verification when feasible.
- Do not store access or refresh tokens in browser storage.

## Testing Rules

- Prefer focused tests near the changed module.
- Backend behavior changes need unit/service tests and integration/contract tests when persistence or event boundaries are involved.
- Kafka changes need envelope/topic tests or consumer tests.
- Tenant/workspace logic needs isolation and missing-context tests.
- Frontend visible changes need lint plus Playwright/browser verification where practical.
- Load/performance-sensitive send, tracking, import, and webhook changes need bounded performance validation or explicit residual risk.

## Quality Gates

Do not push if any relevant gate fails:

- Backend build/tests fail.
- Frontend lint/build/E2E smoke fails.
- Runtime/container health fails.
- Route map, Nginx, or ingress drift exists.
- Logs show critical startup/runtime exceptions.
- Security scans fail or secrets are exposed.
- Tenant/workspace isolation is weakened.
- Kafka correctness, idempotency, retry, or DLQ behavior is weakened.
- Deliverability compliance, suppression, warmup, signed tracking, or inbox safety is weakened.
- Performance regressions are found on high-volume paths.

Commit and push only when explicitly requested and after relevant gates pass.

## Files AI May Modify

When directly relevant:

- `frontend/src/**`, `frontend/tests/**`, frontend config files.
- `services/*/src/main/**`, `services/*/src/test/**`.
- `shared/*/src/main/**`, `shared/*/src/test/**`.
- `config/gateway/route-map.json`, `config/nginx/nginx.conf`, and ingress when kept synchronized.
- `infrastructure/kubernetes/**`.
- `scripts/**`.
- `docs/**`, root documentation, and `.codex/**`.
- `pom.xml`, module `pom.xml`, `frontend/package.json`, and lockfiles only when dependency/build changes require it.

## Files AI Must Not Modify Without Explicit Approval

- `.env`, real secret files, private keys, certificates, local credentials, generated secret material.
- User-created or unrelated dirty files.
- Historical Flyway migrations that may already be applied.
- Generated logs, screenshots, traces, reports, videos, local run artifacts, `target/`, `.next/`, `dist/`, `build/`, coverage, `node_modules/`.
- Deleted demo/documentation bundles or unrelated changed files already present in the worktree.

## Known Current Risks

- Public multi-tenant GA remains blocked by target-environment evidence: production egress, registry digest/SBOM/signature/provenance, live synthetic smoke, live load, restore drill, CI/security transcript, TLS/admission, and monitoring handoff.
- The 10 lakh in 10 hours target needs mature sender reputation, provider capacity approval, load evidence, queue sharding, rate reservations, tracking isolation, and retry/backpressure proof.
- Some Kafka consumers and payload contracts still need broader schema/version strategy.
- Large backend services and frontend route components should be split when touched.
- Public docs/scripts drift has been reduced by this rebuild, but commands must be revalidated after each new utility or workflow change.

## Multi-Thread Autonomous Operation

- Use `.codex/prompts/overall-24x7.md` for one overall coordinating team.
- Use `.codex/utilities/get-module-prompt.ps1 -Module <module>` for one module-level thread.
- Multiple module threads may run in parallel only when registered in `.codex/threads/thread-registry.json` and protected by non-overlapping leases.
- Every module thread must heartbeat, checkpoint before edits, validate its module profile, write compact audit events, and return a handoff.
- Keep `.codex/memory` compact; detailed activity belongs in `.codex/audit/events/`, checkpoints, reports, and `.codex/backlog/queue.json`.
