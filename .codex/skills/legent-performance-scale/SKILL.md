---
name: legent-performance-scale
description: Analyze or improve Legent high-volume performance. Use for 10 lakh send readiness, Kafka partitioning, audience chunking, campaign batching, delivery rate control, provider warmup, tracking ingestion, ClickHouse, imports, webhooks, or load evidence.
---

# Legent Performance Scale

1. Keep warmup, suppression, unsubscribe, signed tracking, inbox safety, idempotency, retry, and DLQ behavior intact.
2. Eliminate unbounded recipient materialization, full JSON payloads, and tenant-only Kafka keys on high-volume paths.
3. Prefer cursor/page checkpoints, durable recipient snapshots, shard-aware keys, batched reservations, and isolated tracking ingestion.
4. Add metrics and load evidence before making throughput claims.
5. Record unproven capacity in `performance-bottlenecks.md`.

Do not claim 10 lakh sends in 10 hours for new or unwarmed domains.

## Measurement Plan

- Define tenant count, workspace count, provider/domain distribution, recipient count, payload shape, target rate, duration, and error budget.
- Track Kafka lag, partition distribution, DB CPU/locks/connections, Redis latency, provider throttles, JVM memory/GC, p95/p99 latency, DLQ volume, stuck jobs, and tracking ingestion lag.
- Separate code bottlenecks from external provider capacity and sender reputation blockers.

## Required Output

- Path audited or changed.
- Bottleneck or evidence gap.
- Safety controls preserved.
- Load evidence or reason it cannot run.
- Memory updates in performance and blocked/risk files.
