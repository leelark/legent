# Performance Bottlenecks

Fresh baseline date: 2026-05-20.
Last compacted: 2026-05-23.

Completed local performance fixes were removed from current-state memory. They remain in queue doneWork/checkpoints.

| Area | Bottleneck or evidence gap | Queue items | Next action |
|---|---|---|---|
| High-volume send | 10 lakh / 10h has no target-like proof with warmed senders and provider approval. | `live-high-volume-proof` | Run provider-approved load test with queue, DB, Redis, ClickHouse, retry/DLQ, and alert metrics. |
| Campaign content | Content-reference writes/fetches and inline fallback policy need load proof. | `campaign-content-reference-target-proof` | Collect target-like content-reference load evidence. |
| Delivery rate control | Local bounded batch reservations reduce same-scope per-message lock round trips, but target DB lock metrics and provider/domain load evidence are still absent. | release/load evidence items | Collect target-like lock, latency, provider throttle, and warmup safety evidence before throughput claims. |
| Delivery outbox | Feedback outbox now has local terminal-row retention cleanup; production still needs live outbox lag, cleanup-run, and database maintenance evidence. | release/load evidence items | Capture target cleanup and lag evidence during runtime validation. |
| Tracking ingestion and analytics | Local batch/idempotency logic and canonical event-id analytics semantics are implemented locally, but live PostgreSQL/ClickHouse throughput, ingestion lag, rollup freshness, replay behavior, and alert evidence are absent. | `tracking-ingestion-batch-consumer-readiness`, release/load evidence items | Collect target runtime evidence and reconcile canonical operational metrics against physical raw-row diagnostics under load. |
| Kafka retry/DLQ | Local retry/DLQ depth, age, skew metrics and GA broker-topology validation exist, but production partition/retention/replication, live lag/DLQ alert delivery, replay drills, and high-volume backpressure proof are absent. | release/load evidence items | Attach reviewed target Kafka topology evidence and run target alert/replay/load drills. |

Standing rule: do not claim high-volume readiness from local tests alone.
