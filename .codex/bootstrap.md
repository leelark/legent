# Legent Codex Bootstrap

Last updated: 2026-05-16.

Use this before any non-trivial work. Never restart from zero when memory exists.

Startup chain:

CTO_AGENT
-> PROGRAM_MANAGER_AGENT
-> Repository intelligence agents
-> Specialized execution agents

CTO_AGENT must start first, approve architecture and risk priorities.

PROGRAM_MANAGER_AGENT must decompose work and spawn parallel subagents automatically.

Maximum concurrent live agents = 6.

When one agent finishes:
assign next highest priority task immediately.

1. Read `AGENTS.md`.
2. Run `git status --short --branch`; preserve unrelated dirty files.
3. Read `.codex/memory/active-work-items.md`, `.codex/memory/blocked-items.md`, `.codex/memory/unresolved-risks.md`, and relevant ownership/risk maps.
4. Check `.codex/checkpoints/` for unfinished checkpoints.
5. Refresh repo facts with `rg --files`, manifests, CI, Compose, route map, and touched module sources.
6. Classify tasks as `NEW_FEATURE`, `ENHANCEMENT`, `BUG_FIX`, `REFACTOR`, `PERFORMANCE`, `SECURITY`, `INCIDENT`, `TESTING`, `RELEASE`, or `ARCHITECTURE`.
7. Score priority: `(ProductionReadinessImpact * 5) + (SecurityRisk * 4) + (UserImpact * 3) + (PerformanceImpact * 2) + TechnicalDebtImpact`.
8. Select smallest coherent change. Add rollback checkpoint for code/config changes.
9. Run impacted validation. Update memory after fix, failure, risk, refactor, or decision.

Current baseline from 2026-05-16 scan:

- Branch baseline for this sweep was `main...origin/main`; repo file count after Loop 5 hardening is 1192 from `rg --files` on 2026-05-16.
- Repo shape: Maven parent with shared modules plus 10 Spring Boot services, Next.js frontend, Docker Compose local runtime, Kubernetes overlays, GitHub CI/security workflow.
- Resolved since original bootstrap: Kafka wildcard trust narrowed, non-test DDL defaults fail closed with `validate`, high-volume Kafka keys avoid tenant-only fallback, audience resolution payloads are chunked/keyset-paged, campaign render/cache and payload guards reduce send pressure, and the dead frontend/source cleanup from 2026-05-16 removed unreachable source files.
- Current local high-priority work completed on 2026-05-16: foundation bootstrap failures rethrow and await critical publishes; automation, deliverability, and audience intelligence idempotency/failure paths now retry instead of silently marking failed side effects as processed; frontend search/mobile shell routing was fixed.
- Current external blockers: production egress needs real CIDR/FQDN/CNI policy data; content/platform workspace-scope semantics need product/security decision; audience V17 production mapping metadata and real GA/load/restore/security evidence remain required.
