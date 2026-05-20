---
name: legent-multi-thread-coordination
description: Coordinate multiple Legent Codex threads and parallel module teams. Use for overall vs module-level 24x7 operation, thread registration, heartbeats, leases, worktrees, handoffs, dashboards, and conflict-free multi-team work.
---

# Legent Multi-Thread Coordination

1. Read `.codex/workflows/multi-thread-autonomous-teams.md`, `.codex/threads/thread-registry.json`, and `.codex/teams/module-team-registry.json`.
2. Choose mode: `OVERALL`, `MULTI_MODULE_COORDINATOR`, or `MODULE`.
3. Register each thread with `.codex/utilities/register-thread.ps1`.
4. Acquire exact leases with `.codex/utilities/acquire-lease.ps1` before edits.
5. Heartbeat every 15-30 minutes with `.codex/utilities/heartbeat-thread.ps1`.
6. In `MULTI_MODULE_COORDINATOR` mode, do not implement module source code or spawn implementation subagents. Use subagents only for backlog triage, dependency mapping, research scouting, validation planning, stale-thread monitoring, release-risk reporting, documentation/memory compaction, and handoff review.
7. Let module threads late-join safely. Coordinator planning is not a blocker; only active overlapping source-code leases, relevant failed validation, missing external evidence, credentials, production access, or explicit human decision can block a module.
8. Monitor with `.codex/utilities/monitor-autonomous-org.ps1`.
9. Validate with `.codex/utilities/validate-codex-system.ps1`.
10. Close threads and release leases after handoff.

Required output:
- mode,
- registered thread id,
- module/team,
- lease scope,
- checkpoint,
- validation,
- handoff and next action.
