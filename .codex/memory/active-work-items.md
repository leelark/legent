# Active Work Items

Fresh baseline date: 2026-05-20.

## Live State

Current overall coordination work:

Overall coordinator `overall-24x7` is ACTIVE in `MULTI_MODULE_COORDINATOR` mode. No implementation item is currently leased by this coordinator.

Active module threads:

| Thread | Owner | Scope |
|---|---|---|
| `foundation-service-20260520T100717Z` | FOUNDATION_SERVICE_OWNER | Existing foundation module thread; read-only module audit in progress. |

Safe-stopped module threads:

| Thread | Owner | Last Work | Resume From |
|---|---|---|---|
| `frontend-20260520T100638Z` | FRONTEND_OWNER | Completed `mode-aware-workflow-contract`: typed BASIC/ADVANCED mode metadata, render-time Settings navigation filtering, Admin role-gate separation, and campaign Experiment Engine render/payload gating. | `.codex/checkpoints/20260520T101846Z-mode-aware-workflow-contract.json`; next safe frontend slice is budget/frequency/template/automation advanced mode metadata. |

Coordinator cleanup:

The duplicate module scouts started by `overall-24x7` for deliverability, content, audience, automation, tracking, and `frontend-20260520T100626Z` were closed or paused after detecting active frontend/foundation module threads. This prevents duplicate module work and shared path overlap.

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
