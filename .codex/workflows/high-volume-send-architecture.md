# High-Volume Send Architecture Workflow

Goal: support 10 lakh email triggers in 10 hours for warmed, authenticated, provider-approved senders without bypassing deliverability controls.

## Required Design Properties

- Recipient work is chunked or snapshot-backed.
- Kafka keys are job/batch/provider/domain/shard aware.
- Rendering is bounded, cached, or precomputed where safe.
- Provider/domain rate reservations are batched or sharded.
- Warmup, suppression, unsubscribe, complaint, bounce, and inbox safety checks stay fail-closed.
- Message logs have retention and partitioning.
- Tracking ingestion is isolated from send execution.
- Backpressure responds to provider capacity, error rate, queue age, and Kafka lag.
- Retry and DLQ paths are observable and idempotent.

## Required Evidence

- Load test with target-like Kafka, DB, Redis, ClickHouse, and provider simulation.
- Metrics for throughput, lag, DB saturation, error rate, retry volume, and tracking ingest.
- Proof that new-domain warmup blocks unsafe volume.
- Stuck-job and replay drill.

## Forbidden Shortcut

Do not disable warmup, suppressions, idempotency, signed tracking, retries, DLQ, or safety checks to hit throughput.
