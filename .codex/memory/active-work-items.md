# Active Work Items

Fresh baseline date: 2026-05-20.

## Live State

No active product implementation work is registered in memory.

Source of truth:
- Live thread/team state: `.codex/threads/thread-registry.json`
- Live assignments: `.codex/state/team-state.json`
- Work queue and history: `.codex/backlog/queue.json`
- Detailed activity trail: `.codex/audit/events/YYYY-MM-DD.jsonl`
- Session checkpoints: `.codex/checkpoints/*.json`
- Narrative evidence: `.codex/reports/`

## Next Action

Run one of:
- overall mode: `.codex/utilities/get-module-prompt.ps1 -Module overall`
- module mode: `.codex/utilities/get-module-prompt.ps1 -Module <module>`

Then follow the rendered prompt. If no READY item exists, run `pending-scan`, `research-pass`, `refine-backlog`, or promote a scoped refined backlog item with `.codex/utilities/promote-backlog-item.ps1`.

## Memory Budget Rule

Keep this file short. Do not paste completed-work history or command logs here. Store durable completions in `.codex/backlog/queue.json`, reports, and the owning memory file.
