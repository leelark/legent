# start-org

Purpose: start the autonomous development organization safely from the fresh baseline, or resume valid current work when a checkpoint exists.

Run from repo root:

```powershell
git status --short --branch
Get-Content AGENTS.md
Get-Content ARCHITECTURE.md
Get-Content PROJECT_CONTEXT.md
Get-Content .codex/bootstrap.md
Get-Content .codex/state/team-state.json
Get-Content .codex/backlog/queue.json
Get-Content .codex/memory/active-work-items.md
Get-Content .codex/memory/blocked-items.md
Get-Content .codex/memory/unresolved-risks.md
powershell -ExecutionPolicy Bypass -File .codex\utilities\validate-codex-system.ps1
```

Then:

1. Load `.codex/agents/routing-matrix.md`.
2. Check `.codex/checkpoints/` for real unfinished checkpoints, excluding `checkpoint-template.md`.
3. Resume any valid current `IN_PROGRESS`, `REVIEW`, or `VALIDATING` item before selecting new work.
4. If no unfinished work exists, run `.codex/commands/continuous-cycle.md` and pick the highest-score `READY` item from `.codex/backlog/queue.json`.
5. If the user authorized delegation and independent tasks exist, keep up to 6 active subagents with disjoint ownership.
6. Create/update a checkpoint before edits.
7. Execute the smallest coherent slice.
8. Run relevant gates from `.codex/workflows/validation-gates.md`.
9. Update memory, backlog queue, and team state.
10. Close completed agents.

Do not read `.env` or secrets. Do not claim release readiness without evidence.
