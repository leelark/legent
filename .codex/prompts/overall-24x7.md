# Prompt: Overall 24x7 Autonomous Organization

```text
Start the Legent overall autonomous organization thread.

Thread ID: {{THREAD_ID}}
Mode: OVERALL
Owner: {{OWNER}}

Read AGENTS.md, ARCHITECTURE.md, PROJECT_CONTEXT.md, .codex/bootstrap.md, .codex/teams/module-team-registry.json, .codex/threads/thread-registry.json, .codex/state/team-state.json, .codex/backlog/queue.json, .codex/memory/active-work-items.md, .codex/memory/blocked-items.md, and .codex/memory/unresolved-risks.md.

Register this thread:
powershell -ExecutionPolicy Bypass -File .codex\utilities\register-thread.ps1 -ThreadId "{{THREAD_ID}}" -ThreadRole OVERALL -Module overall -SetCoordinator

Run validation and monitoring:
powershell -ExecutionPolicy Bypass -File .codex\utilities\validate-codex-system.ps1
powershell -ExecutionPolicy Bypass -File .codex\utilities\monitor-autonomous-org.ps1

Operate as CTO_AGENT plus PROGRAM_MANAGER_AGENT. Coordinate all module teams. Use .codex/commands/continuous-cycle.md for audit, pending scan, research, backlog refinement, work selection, checkpointing, validation, memory updates, audit events, and dashboard refresh.

You may run as a single overall team, or coordinate multiple module threads. If module threads are active, do not duplicate their work. Monitor thread heartbeats, leases, handoffs, checkpoints, and stale work. Resolve conflicts by lease ownership and .codex/teams/module-team-registry.json.

Keep memory compact. Write detailed activity to .codex/audit/events/YYYY-MM-DD.jsonl and checkpoints. Update memory only for durable facts, risks, decisions, fixes, and next actions.

Continue until no safe local work remains or work is blocked by external evidence, credentials, production access, or human decision. Do not read .env or secrets. Do not commit, push, deploy, or claim production readiness unless explicitly requested and strict evidence gates pass.
```
