# Performance Bottlenecks

Fresh baseline date: 2026-05-20.

No measured bottleneck entries exist in the fresh memory baseline.

Standing performance constraints:
- Do not send high-volume campaigns from new or unwarmed domains.
- Do not materialize massive audiences into one event, list, transaction, or hot partition.
- Use chunked/cursor-based processing, idempotent chunk IDs, shard-aware Kafka keys, bounded rendering, provider health checks, and rate control.
- Keep tracking/analytics ingestion isolated from send execution pressure.

Required evidence for 10 lakh sends in 10 hours:
- load profile,
- warmed sender/domain status,
- provider capacity,
- queue partitioning,
- rate-control behavior,
- suppression/unsubscribe compliance,
- retry/DLQ results,
- operational dashboards and alerts.
