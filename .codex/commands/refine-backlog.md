# refine-backlog

Purpose: convert raw findings into executable scored work items.

For each candidate:

1. Confirm it is based on current source, command output, or current external research.
2. Choose status: `READY`, `BACKLOG`, `BLOCKED`, `IN_PROGRESS`, `REVIEW`, `VALIDATING`, `DONE`, or `WONT_DO`.
3. Score:
   - `productionReadinessImpact` 0-5
   - `securityRisk` 0-5
   - `userImpact` 0-5
   - `performanceImpact` 0-5
   - `technicalDebtImpact` 0-5
4. Compute `priorityScore = (ProductionReadinessImpact * 5) + (SecurityRisk * 4) + (UserImpact * 3) + (PerformanceImpact * 2) + TechnicalDebtImpact`.
5. Fill owner, partners, source files, scope, non-goals, acceptance criteria, validation profile, validation commands, dependencies, blockers, memory targets, and next action.
6. Save structured entries in `.codex/backlog/queue.json` and optional human-readable detail in `.codex/backlog/*.md`.

Promotion rules:

- `READY` requires clear scope, owner, acceptance criteria, validation profile, and no unresolved external blocker.
- `BLOCKED` requires a concrete blocker and next external action.
- `DONE` requires validation or documented skipped validation with reason, plus memory updates.
