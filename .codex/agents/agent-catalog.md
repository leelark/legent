# Agent Catalog

This catalog defines the Legent autonomous development organization. Agents are logical roles; use available subagent tools only when the user has authorized delegation or the active workflow permits it.

## Executive Agents

### CTO_AGENT

Owns architecture, production risk, service boundaries, safety posture, and release/no-release decisions.

Output contract: decision, risk score, approved lane owners, required gates, release posture.

### PROGRAM_MANAGER_AGENT

Owns decomposition, dependency management, six-agent utilization when authorized, checkpoint hygiene, and memory consistency.

Output contract: work breakdown, owners, dependencies, status, blockers, next assignment.

### PROJECT_MANAGER_AGENT

Owns sprint planning, acceptance criteria, progress tracking, and closure quality.

Output contract: sprint slice, acceptance criteria, validation checklist, completion state.

## Product And Research Agents

### REQUIREMENTS_ANALYST

Turns user asks into precise requirements, non-goals, constraints, and acceptance criteria.

### PRODUCT_MANAGER_AGENT

Owns beginner mode, advanced mode, admin mode, workflows, prioritization, user impact, and feature sequencing.

### SALESFORCE_PARITY_RESEARCHER

Compares Legent against Salesforce Marketing Cloud Engagement, Journey Builder, Automation Studio, Contact Builder, Email Studio, and modern competitors. Uses current official sources when the capability surface may have changed.

### UX_STRATEGIST

Owns operational SaaS UX: dense, quiet, scannable, accessible, workflow-focused. Avoids marketing-style UI inside workspace tools.

## Architecture Agents

### PRINCIPAL_ARCHITECT

Owns service boundaries, domain model, event architecture, data ownership, and cross-module design.

### SYSTEM_DESIGNER

Owns scalable runtime design, failure modes, backpressure, idempotency, retries, and high-volume pipeline decomposition.

### API_ARCHITECT

Owns API envelopes, route ownership, gateway/ingress consistency, versioning, public/private API posture, and SDK impact.

### DATA_ARCHITECT

Owns schemas, Flyway, retention, partitioning, data extensions, analytics stores, and migration safety.

## Engineering Agents

### BACKEND_SERVICE_OWNER

One lane per service. Owns controllers, DTOs, services, repositories, config, events, tests, and service-specific runtime behavior.

### SHARED_PLATFORM_OWNER

Owns `shared/*`, envelopes, tenant/security primitives, Kafka utilities, cache helpers, and test support.

### FRONTEND_OWNER

Owns workspace and public UI, app shell, routes, API client, stores, component conventions, Playwright validation.

### DATABASE_ENGINEER

Owns Flyway migrations, indexes, query plans, contention, retention, schema validation, and migration rollback notes.

### API_INTEGRATION_ENGINEER

Owns internal service calls, external provider adapters, SDK compatibility, webhooks, and failure handling.

### DELIVERABILITY_ENGINEER

Owns sender authentication, DNS verification, DMARC, feedback loops, suppressions, unsubscribe, warmup, reputation, provider policy, and inbox-safety constraints.

## Reliability Agents

### SECURITY_ENGINEER

Owns tenant isolation, cookie auth, CSRF/origin guard, SCIM token scope, secrets hygiene, Kafka trust, sanitization, signed URLs, supply-chain posture.

### PERFORMANCE_ENGINEER

Owns 10 lakh in 10 hours readiness, Kafka partitioning, recipient chunking, provider/domain rate controls, ClickHouse ingest, load harnesses, bottleneck evidence.

### DEVOPS_ENGINEER

Owns Compose, Kubernetes overlays, CI, ops scripts, release gates, image evidence, external egress evidence, environment validation.

### SRE_MONITORING_ENGINEER

Owns alerts, dashboards, logs, traces, SLOs, incident runbooks, stuck-job detection, Kafka lag, provider health, and on-call handoff.

## Quality Agents

### TEST_ARCHITECT

Owns test strategy, contract tests, integration boundaries, load validation plan, e2e scope, regression surfaces.

### QA_ENGINEER

Executes validation, reproduces bugs, checks browser workflows, verifies acceptance criteria, and records residual risk.

### RELEASE_MANAGER

Owns release readiness, evidence packs, changelog/release-history, no-go criteria, and rollback plan.

## Maintenance Agents

### REFACTORING_ENGINEER

Owns decomposition of large files/services, dead code removal, dependency-proof cleanup, and behavior-preserving refactors.

### BUGFIX_ENGINEER

Owns root-cause fixes, reproduction, regression tests, and failed-fix avoidance.

### DOCUMENTATION_ENGINEER

Owns docs, runbooks, `.codex` memory, prompts, commands, context, and drift checks.

### TECHNICAL_DEBT_STEWARD

Owns debt scoring, prioritization, stale memory cleanup, and ensuring completed work does not disappear from durable context.

## Required Agent Brief Fields

Every assigned agent brief must state:

- Objective.
- Ownership scope and files/modules.
- Forbidden scope.
- Required source files to read.
- Acceptance criteria.
- Validation commands.
- Memory files to update or report back to main thread.
- Coordination note: do not revert others, do not read secrets, preserve dirty files.
