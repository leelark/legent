# Multi-Thread Autonomous Teams

Use either mode:
- Overall mode: one coordinator thread runs `.codex/prompts/overall-24x7.md`.
- Multi-module mode: one coordination-only thread runs `.codex/prompts/multi-module-coordinator-24x7.md`, then one or more module threads run prompts rendered by `.codex/utilities/get-module-prompt.ps1 -Module <module>`.

Coordination:
- Register every thread in `.codex/threads/thread-registry.json`.
- Acquire exact leases before edits.
- Heartbeat every 15-30 minutes during active work.
- Treat heartbeat stale at 60 minutes and pause at 120 minutes.
- Lease TTL defaults to 240 minutes; renew or release before expiry.
- Detailed activity goes to `.codex/audit/events/YYYY-MM-DD.jsonl`.
- Memory files stay compact and durable.

Overall thread responsibilities:
- prioritize backlog,
- coordinate module leases,
- monitor stale threads,
- resolve conflicts,
- own release posture,
- merge handoffs into memory/reports.

Multi-module coordinator restrictions:
- The coordinator may start before module teams and must not mark missing module threads as blocked.
- The coordinator owns planning, assignment, monitoring, stale-thread cleanup, research, validation planning, handoff review, compact memory, reports, dashboards, and backlog hygiene.
- The coordinator must not edit module source code, acquire broad source leases, or spawn implementation subagents for frontend, services, shared, infra, config, or scripts.
- Coordinator subagents may only perform coordination-support work. Implementation findings become module backlog items.
- Use short exact metadata leases and release them quickly so late-starting module threads can register, checkpoint, and hand off without waiting on broad `.codex/**` locks.

Module thread responsibilities:
- stay inside allowed paths,
- start safely even if the coordinator ran first,
- treat coordinator planning as assignment, not as a blocker,
- continue source work when only a shared `.codex` metadata write is temporarily leased,
- create checkpoints,
- run focused validation,
- write handoff,
- release leases,
- update audit trail.
