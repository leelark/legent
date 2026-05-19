---
name: legent-autonomous-org
description: Run the project-local Legent autonomous development organization. Use when coordinating multi-agent work, rebuilding .codex orchestration, routing tasks, creating checkpoints, resuming work, or managing durable project memory.
---

# Legent Autonomous Org

1. Read `AGENTS.md`, `ARCHITECTURE.md`, `PROJECT_CONTEXT.md`, `.codex/bootstrap.md`, and `.codex/state/team-state.json`.
2. Run `.codex/utilities/validate-codex-system.ps1`.
3. Read `.codex/backlog/queue.json`, `.codex/memory/active-work-items.md`, `.codex/memory/blocked-items.md`, and `.codex/memory/unresolved-risks.md`.
4. Treat `.codex/memory` as a fresh baseline from 2026-05-20; do not trust older memory claims unless revalidated.
5. Use `.codex/agents/routing-matrix.md` to assign owners.
6. Check for unfinished checkpoints and active `IN_PROGRESS`, `REVIEW`, or `VALIDATING` work before selecting new `READY` work.
7. If no valid active work exists, use `.codex/commands/continuous-cycle.md`.
8. Use up to 6 active subagents only when delegation is authorized and independent work exists.
9. Keep ownership disjoint; use `.codex/worktrees/leases/active-leases.json` when parallel workers may write.
10. Record live work in `.codex/state/team-state.json`, `.codex/backlog/queue.json`, and `.codex/memory/active-work-items.md`.
11. Create checkpoints for multi-file or long-running work.
12. Run gates from `.codex/workflows/validation-gates.md`.
13. Merge findings into memory before final response.

Never read `.env` or secrets. Never claim release readiness without evidence.

## Required Output

- Objective and selected work item.
- Owner, partners, and file/module scope.
- Subagents used or reason none were useful.
- Validation run and validation not run.
- Memory/backlog/checkpoint updates.
- Residual risks and next action.
