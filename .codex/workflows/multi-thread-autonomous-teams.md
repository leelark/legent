# Multi-Thread Autonomous Teams

Use either mode:
- Overall mode: one coordinator thread runs `.codex/prompts/overall-24x7.md`.
- Module mode: one or more module threads run prompts rendered by `.codex/utilities/get-module-prompt.ps1 -Module <module>`.

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

Module thread responsibilities:
- stay inside allowed paths,
- create checkpoints,
- run focused validation,
- write handoff,
- release leases,
- update audit trail.
