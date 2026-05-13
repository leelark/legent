# Legent Codex Bootstrap

Last updated: 2026-05-13.

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

Current baseline from 2026-05-13 scan:

- Branch: `main...origin/main`; `AGENTS.md` was already modified before orchestration setup.
- Repo shape: Maven parent with shared modules plus 10 Spring Boot services, Next.js frontend, Docker Compose local runtime, Kubernetes overlays, GitHub CI/security workflow.
- `.codex/memory` was missing and created during orchestration setup.
- Critical open risks: wildcard Kafka trusted packages in 6 services, service defaults for `ddl-auto:update`, tenant-key Kafka publishing default, full resolved audience payload in one event, per-recipient render in send loop, large frontend/backend hotspots.
