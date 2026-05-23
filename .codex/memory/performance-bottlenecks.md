# Performance Bottlenecks

Fresh baseline date: 2026-05-20.
Last compacted: 2026-05-23.

Completed local performance fixes were removed from current-state memory. They remain in queue doneWork/checkpoints.

| Area | Bottleneck or evidence gap | Queue items | Next action |
|---|---|---|---|
| High-volume send | 10 lakh / 10h has no target-like proof with warmed senders and provider approval. | `live-high-volume-proof` | Run provider-approved load test with queue, DB, Redis, ClickHouse, retry/DLQ, and alert metrics. |
| Audience resolution | Chunk payloads can still carry subscriber lists rather than durable metadata-only references. | `audience-resolution-metadata-only-chunks` | Design durable chunk/snapshot handoff. |
| Campaign content | Content-reference writes/fetches and inline fallback policy need load proof. | `campaign-content-reference-target-proof` | Collect target-like content-reference load evidence. |
| Delivery rate control | Provider/domain rate reservations can still contend on hot state. | `delivery-rate-control-sharded-reservations` | Design sharded/batched reservations and metrics. |
| Delivery outbox | Feedback outbox needs retention/cleanup after decoupling. | `delivery-feedback-outbox-retention-cleanup` | Add safe terminal-row cleanup. |
| Tracking ingestion | Local batch/idempotency logic is not live PostgreSQL/ClickHouse proof. | `tracking-ingestion-batch-consumer-readiness` | Collect runtime evidence and reconciliation proof. |
| Kafka retry/DLQ | Production partition/retention/replication and retry/DLQ alert evidence are absent. | `retry-dlq-target-readiness` | Add readiness plan and target evidence. |

Standing rule: do not claim high-volume readiness from local tests alone.
