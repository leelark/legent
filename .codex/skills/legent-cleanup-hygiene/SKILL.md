---
name: legent-cleanup-hygiene
description: Clean stale autonomous organization state, old leases, stale threads, stale worktrees, dashboard drift, and memory/log bloat without losing durable project intelligence.
---

# Legent Cleanup Hygiene

1. Run `.codex/utilities/validate-codex-system.ps1`.
2. Run `.codex/utilities/monitor-autonomous-org.ps1`.
3. Run `.codex/utilities/cleanup-autonomous-org.ps1 -DryRun`.
4. If safe, pause stale threads and remove stale active leases with cleanup utility.
5. Keep `.codex/memory` compact; move detail to audit events, checkpoints, reports, and queue history.
6. Do not delete checkpoints, reports, or audit events unless the user explicitly asks for archival cleanup.

Required output:
- stale items found,
- cleanup performed,
- files changed,
- validation,
- residual risk.
