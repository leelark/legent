# Legent Autonomous Organization Bootstrap

Last rebuilt: 2026-05-20.

Use this file before every non-trivial Legent engineering session. This repository is on a fresh `.codex/memory` baseline as of 2026-05-20; prior memory content is not authoritative unless revalidated from current source files, commands, or evidence.

## Startup Sequence

1. Read `AGENTS.md`, `ARCHITECTURE.md`, `PROJECT_CONTEXT.md`, and this file.
2. Run `git status --short --branch` and preserve unrelated dirty files.
3. Validate `.codex` operating state with `.codex/utilities/validate-codex-system.ps1`.
4. Read `.codex/state/team-state.json`, `.codex/backlog/queue.json`, `.codex/memory/active-work-items.md`, `.codex/memory/blocked-items.md`, and `.codex/memory/unresolved-risks.md`.
5. Check `.codex/checkpoints/` for unfinished real checkpoints (`*.json` preferred; Markdown only if it contains a valid JSON checkpoint block).
6. Refresh facts for the touched area with `rg --files`, manifests, route map, CI, Compose, and implementation sources.
7. Choose operating mode:
   - `OVERALL`: one coordinator thread from `.codex/prompts/overall-24x7.md`.
   - `MODULE`: one module thread rendered by `.codex/utilities/get-module-prompt.ps1 -Module <module>`.
   - `HYBRID`: one overall coordinator plus multiple registered module threads.
8. If no valid active work exists, run `.codex/commands/continuous-cycle.md`: audit, pending scan, research, refine backlog, promote one actionable backlog item when needed, select highest-score ready item, implement, validate, record, repeat.
9. Classify work as one or more of: `REQUIREMENTS`, `PRODUCT`, `ARCHITECTURE`, `SYSTEM_DESIGN`, `BACKEND`, `FRONTEND`, `DATABASE`, `API`, `INFRA`, `DEVOPS`, `SECURITY`, `PERFORMANCE`, `TESTING`, `QA`, `MONITORING`, `DOCUMENTATION`, `REFACTOR`, `BUGFIX`, `RELEASE`, `RESEARCH`.
10. Score priority: `(ProductionReadinessImpact * 5) + (SecurityRisk * 4) + (UserImpact * 3) + (PerformanceImpact * 2) + TechnicalDebtImpact`.
11. Assign owners through `.codex/agents/routing-matrix.md`.
12. Register thread/lease and create or update checkpoint state before file edits.
13. Run impacted validation and update compact memory, audit events, dashboard, and backlog when work completes or fails.

## Executive Chain

`CTO_AGENT` owns architecture, risk, and release posture.

`PROGRAM_MANAGER_AGENT` owns decomposition, dependencies, priority, checkpoints, and subagent utilization.

`PRODUCT_MANAGER_AGENT` owns requirements, Salesforce-parity capability mapping, beginner/advanced/admin modes, and user impact.

`PROJECT_MANAGER_AGENT` owns sprint slices, work item lifecycle, acceptance criteria, and progress reporting.

Execution agents own implementation and validation inside their lane.

## Parallel Team Rule

When parallel work is authorized and independent work exists, maintain up to 6 active subagents with disjoint ownership:

- One agent per independent domain or file set.
- Read-only discovery before broad refactors.
- Workers must be told they are not alone in the codebase and must not revert other edits.
- No subagent may commit, push, alter secrets, or rewrite unrelated dirty files.
- When an agent completes, close it after consuming results and assign another useful independent task if work remains.
- If no independent work remains, stop spawning and finish integration locally.

Record live assignments in `.codex/state/team-state.json` under `activeAgents` and summarize them in `.codex/memory/active-work-items.md`.
Use `.codex/worktrees/leases/active-leases.json` and `.codex/utilities/validate-worktree-leases.ps1` when parallel work may touch overlapping files or modules.

For multiple Codex threads, register every thread in `.codex/threads/thread-registry.json`, use module teams from `.codex/teams/module-team-registry.json`, and validate with `.codex/utilities/validate-thread-coordination.ps1`.

Heartbeat cadence:
- active thread heartbeat: every 15-30 minutes,
- stale warning: 60 minutes,
- automatic pause cleanup: 120 minutes,
- lease expiry default: 240 minutes,
- dashboard refresh: every cycle and at least daily.

## Default Organization Lanes

- Executive: CTO, Program Manager, Project Manager.
- Product: Requirements Analyst, Product Manager, Salesforce Parity Researcher, UX Strategist.
- Architecture: Principal Architect, System Designer, API Architect, Data Architect.
- Backend: one owner per service plus shared modules owner.
- Frontend: Workspace UI Owner, Public UI Owner, Frontend Platform Owner.
- Reliability: Security Engineer, Performance Engineer, DevOps Engineer, SRE/Monitoring Engineer.
- Quality: Test Architect, QA Engineer, Release Manager.
- Maintenance: Refactoring Engineer, Bugfix Engineer, Documentation Engineer, Technical Debt Steward.

See `.codex/agents/agent-catalog.md` for responsibilities and output contracts.

## Work Item Lifecycle

States:

- `BACKLOG`: known but not selected.
- `READY`: scoped, unblocked, owner and validation known.
- `IN_PROGRESS`: assigned and actively being worked.
- `BLOCKED`: waiting for external evidence, human decision, missing secret, missing environment, or dependency.
- `REVIEW`: implementation complete, needs review/validation.
- `VALIDATING`: gates running.
- `DONE`: merged into current workspace, memory updated, residual risk recorded.
- `WONT_DO`: intentionally rejected with reason.

No work item is `DONE` until validation and memory updates are complete or skipped with a concrete reason.

## Checkpoint Rules

Create a checkpoint for:

- Multi-file changes.
- Risky security/performance/release work.
- Long autonomous sessions.
- Any interruption-prone task.

Use `.codex/checkpoints/checkpoint-template.md`. A checkpoint must include objective, owner, files in scope, current status, validation plan, rollback notes, blockers, and next action.

## Memory Rules

Current-state memory should stay concise. Use reports for long audits and histories. Required memory updates:

- `successful-fixes.md`, `root-cause-history.md`, and `release-history.md` after meaningful fixes.
- `failed-fixes.md` after failed attempts.
- `security-findings.md` for security posture changes.
- `performance-bottlenecks.md` for scalability findings or load results.
- `technical-debt.md` for debt created, reduced, or reprioritized.
- `design-decisions.md` for architecture/product operating decisions.
- `active-work-items.md`, `blocked-items.md`, and `unresolved-risks.md` for live state.

Never store secrets, raw tokens, `.env` values, private keys, or customer data.

For token efficiency, write detailed activity to `.codex/audit/events/YYYY-MM-DD.jsonl` and checkpoints. Keep memory files limited to durable facts, risks, decisions, fixes, and next actions.

## Gate Policy

Relevant gates must pass before release-quality claims:

- Backend: `.\mvnw.cmd test` or focused `-pl <module> -am test`.
- Frontend: `npm run lint`, `npm run build:ci`, impacted Playwright tests.
- Routing: `scripts\ops\validate-route-map.ps1`.
- Runtime: `docker compose config --quiet` and optional Compose health validation.
- Production: `kubectl kustomize infrastructure/kubernetes/overlays/production` and `scripts\ops\validate-production-overlay.ps1`.
- Security: repo artifact hygiene, npm audit, gitleaks/Trivy in CI.
- Release: `scripts\ops\release-gate.ps1` with strict evidence flags when evaluating promotion.

Do not invent evidence. If live load, image provenance, egress, restore, or monitoring evidence is missing, keep release blocked.

## Recovery

On interruption:

1. Read `.codex/prompts/recovery.md`.
2. Validate `.codex` operating state with `.codex/utilities/validate-codex-system.ps1`.
3. Read the latest real checkpoint in `.codex/checkpoints/`, excluding `checkpoint-template.md`.
4. Re-run `git status --short --branch`.
5. Re-open `.codex/state/team-state.json`, `.codex/backlog/queue.json`, and active/blocked/risk memory.
6. Resume the newest valid `IN_PROGRESS`, `REVIEW`, or `VALIDATING` item, unless the user provides a newer request. If none exists, continue with the highest-score ready backlog item.

## Completion Standard

An autonomous work cycle is complete only when:

- Implementation or document changes are made.
- Relevant validation ran or was explicitly not run with reason.
- Memory/state/checkpoints are updated.
- Backlog queue is updated when work is selected, blocked, completed, or newly discovered.
- Finished subagents are closed.
- Final response includes changed files, validation, residual risks, and next action.
