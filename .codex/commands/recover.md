# recover

Purpose: resume from the newest valid current checkpoint after interruption. The fresh memory baseline starts on 2026-05-20; older memory content is not authoritative unless revalidated.

Run:

```powershell
git status --short --branch
Get-Content .codex/prompts/recovery.md
Get-Content .codex/state/team-state.json
Get-Content .codex/backlog/queue.json
Get-Content .codex/memory/active-work-items.md
Get-Content .codex/memory/blocked-items.md
Get-Content .codex/memory/unresolved-risks.md
Get-ChildItem .codex\checkpoints -File | Where-Object { $_.Name -ne 'checkpoint-template.md' } | Sort-Object LastWriteTime -Descending | Select-Object -First 5
powershell -ExecutionPolicy Bypass -File .codex\utilities\validate-codex-system.ps1
```

Then:

1. Read newest relevant real checkpoint. Prefer `.json` checkpoints. If none exists, use `.codex/state/team-state.json`, `.codex/backlog/queue.json`, and `.codex/memory/active-work-items.md`.
2. Determine whether the user supplied a newer request.
3. Resume the newest valid `IN_PROGRESS`, `REVIEW`, or `VALIDATING` item. If none exists, select the highest-score `READY` item.
4. Recreate agent assignments only when independent work remains.
5. Finish validation and memory updates.
