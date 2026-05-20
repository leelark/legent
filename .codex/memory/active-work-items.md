# Active Work Items

Fresh baseline date: 2026-05-20.

## Live State

Current overall implementation work:

No active implementation item is currently leased by the overall thread.

Parallel read-only agents:

| Work Item | Owner | Scope |
|---|---|---|
| backend/campaign next item discovery | CAMPAIGN_SERVICE_OWNER | Completed read-only scout; campaign legacy eligibility marker remains blocked pending compatibility decision. |
| frontend/product next item discovery | FRONTEND_OWNER | Completed read-only scout; selected Automation Studio run-history visibility. |
| automation/journey next item discovery | AUTOMATION_SERVICE_OWNER | Completed read-only scout; selected Automation Studio run-history visibility as child slice. |
| data/audience next item discovery | AUDIENCE_SERVICE_OWNER | Completed read-only scout; data-extension governance migration tests remain a safe future candidate. |
| security/deliverability next item discovery | SECURITY_ENGINEER | Completed read-only scout; email governance policy objects remain a safe future candidate. |
| devops/release next item discovery | DEVOPS_ENGINEER | Completed read-only scout; release-evidence validator negative fixtures remain a safe future candidate. |

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
