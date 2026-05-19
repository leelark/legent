---
name: legent-bugfix-root-cause
description: Fix Legent bugs by reproducing behavior, tracing root cause, adding regression coverage, validating impact, and updating durable memory.
---

# Legent Bugfix Root Cause

1. Reproduce the smallest failing route, service, event, query, or UI action.
2. Trace actual implementation before editing.
3. Identify frontend, backend, gateway, database, Kafka, send-pipeline, or cross-service ownership.
4. Fix the root cause with the smallest maintainable change.
5. Add regression coverage that fails before the fix where feasible.
6. Check performance impact on per-recipient, per-message, per-event, and per-render paths.
7. Update `bug-history.md`, `root-cause-history.md`, `successful-fixes.md`, and `release-history.md` when applicable.

Rules:
- never hide errors,
- never suppress exceptions without retry/alert/status,
- never weaken tenant isolation or deliverability safety,
- never edit old migrations.

Required output:
- reproduction,
- root cause,
- changed files,
- regression test,
- validation,
- memory updates.
