# Root Cause History

Fresh baseline date: 2026-05-20.
Last compacted: 2026-05-23.

Verbose fixed-item narratives were removed from current-state memory. Detailed root-cause and validation context remains in queue doneWork, checkpoints, and audit events.

## Current Avoidance Patterns

- Async tests need explicit latches/futures and realistic bounded waits.
- Public auth helpers must stay credentialless/contextless; workspace auth helpers must use explicit context allowlists.
- Tenant/workspace/environment context must be part of every protected backend and frontend workflow.
- High-volume paths must avoid full audience payloads, all-row reads, hot-row contention, and synchronous per-recipient pressure.
- Tracking analytics must distinguish physical raw rows from canonical deduped operational events.
- Kafka high-volume topics need shard-aware keys, local drift tests, and separate production broker evidence.
- Outbox patterns need retention, retry, stuck-row metrics, and target throughput proof.
- Release validators prove local hygiene only; target evidence is still required for promotion.

Future root-cause entries should be concise and current. Completed details belong in queue/checkpoints/audit.
