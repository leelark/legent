# start-org

Purpose: resume autonomous engineering safely.

Run from repo root:

```powershell
git status --short --branch
Get-Content AGENTS.md
Get-Content .codex/bootstrap.md
Get-Content .codex/memory/active-work-items.md
Get-Content .codex/memory/blocked-items.md
Get-Content .codex/memory/unresolved-risks.md
```

Then:

1. Refresh dependency, ownership, hotspot, and change-risk maps for touched areas.
2. Restore unfinished work from `.codex/memory/active-work-items.md`.
3. Resume blocked work only when blocker is gone.
4. Prioritize by production-readiness score.
5. Spawn agents automatically for independent ready work with disjoint ownership. If independent tasks >= 2, single-agent execution is forbidden.
6. Merge findings into memory before changing code.
