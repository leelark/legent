# High-Volume Readiness

The 10 lakh email triggers in 10 hours target requires about 27.8 accepted send attempts per second before retries and tracking volume. This is achievable only for warmed, authenticated, provider-approved senders with real infrastructure capacity.

## Required Proof

- Audience resolution emits bounded chunks or durable snapshots.
- Campaign execution stores row/object-backed recipient state.
- Kafka keys are shard-aware by job, batch, provider, domain, or shard.
- Delivery reserves provider/domain capacity without hot single-row contention.
- Warmup, suppressions, unsubscribe, bounce/complaint, signed tracking, and inbox safety remain enabled.
- Tracking ingestion and ClickHouse rollups handle generated event volume.
- Retry/DLQ, stuck-job, replay, and backpressure behavior are observable.
- Production Kafka broker evidence covers broker count, availability zones, replication factor, min ISR, `acks=all`, disabled auto-topic creation, topic partitions, DLQ retention, under-replicated/offline partitions, consumer lag, and alert routing.

## Evidence Metrics

- Sends per second and accepted provider handoffs.
- Kafka lag by topic/partition.
- Retry depth, oldest retry age, DLQ depth, oldest DLQ age, and DLQ skew by low-cardinality queue/source label.
- DB CPU, locks, slow queries, connection pool saturation.
- Redis latency and token reservation contention.
- ClickHouse insert latency, partitions, TTL.
- Delivery provider error rate, throttling, bounces, complaints.
- Tracking ingest throughput and dedupe rate.
- End-to-end campaign completion time.

No throughput claim should be made without a dated target-like load report.
