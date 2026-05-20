# Performance Bottlenecks

Fresh baseline date: 2026-05-20.

Audit-only bottlenecks from 2026-05-20 current source review. These are not measured load results.

| ID | Owner | Status | Source | Impact | Next Action |
|---|---|---|---|---|---|
| audience-bulk-suppression-check | AUDIENCE_SERVICE_OWNER | FIXED | `AudienceResolutionConsumer.java`, `DeliverabilityServiceClient.java`, `SuppressionController.java`, `SuppressionListRepository.java`, `V10__suppression_bulk_lookup_index.sql` | Audience resolution now sends normalized, deduped candidate emails to a bounded deliverability bulk-check endpoint instead of fetching all workspace suppressions. | Monitor high-volume resolution metrics; live throughput proof still requires target-like load evidence. |
| delivery-feedback-outbox | DELIVERY_SERVICE_OWNER | FIXED-CODE | `DeliveryFeedbackOutboxService.java`, `DeliveryFeedbackOutboxEvent.java`, `V15__delivery_feedback_outbox_events.sql`, `DeliveryOrchestrationService.java` | Delivery sent/failed/bounced/retry feedback now writes a small durable outbox row and retries Kafka publication without changing provider send state. | Add live depth/oldest-age alerting, retention cleanup, and target-like outbox throughput evidence before high-volume claims. |
| campaign-send-content-reference-contract | CAMPAIGN_SERVICE_OWNER | FIXED-CONTRACT | `SendExecutionService.java`, `application.yml`, `SendExecutionServiceTest.java`, `EventContractValidatorTest.java` | Send-request events now require and carry durable content references, preventing inline-only Kafka sends; optional inline fallback can still add per-recipient body bytes when enabled. | Run target-like proof for per-recipient content snapshot writes, delivery reference fetches, queue lag, and decide whether production high-volume mode must disable inline fallback. |
| tracking-ingress-rate-policy | DEVOPS_ENGINEER | FIXED-CONFIG | `config/nginx/nginx.conf`, `infrastructure/kubernetes/ingress/ingress.yml`, `scripts/ops/validate-route-map.ps1`, `docs/operations/ga-evidence-matrix.md` | Local Nginx and Kubernetes ingress now explicitly align `/api/v1/tracking` to a reviewed elevated edge policy while keeping analytics and websocket routes on normal API limits. Kubernetes uses community ingress-nginx `limit-rps`, which is enforced per controller replica. This is protective configuration only. | Collect target-environment evidence for ingress 429 behavior, accepted/rejected rates, p95/p99 latency, tracking-service errors, Kafka lag, ClickHouse insert latency, outbox depth/age, dedupe rate, and alert routing before throughput claims. |
| delivery-rate-control-hot-path | DELIVERY_SERVICE_OWNER | BLOCKED-CODE | `DeliveryOrchestrationService.java`, `SendRateControlService.java`, `ProviderCapacityService.java` | Per-message DB lock/reservation path and prelaunch-only capacity decisions are not target-proven for mature high-volume sends. | Design sharded/batched provider-domain reservations and hot-path provider capacity enforcement. |
| tracking-consumer-throughput | TRACKING_SERVICE_OWNER | BLOCKED-CODE-EVIDENCE | `TrackingOutboxService.java`, `TrackingEventConsumer.java`, `KafkaTopicConfig.java` | Tracking uses outbox/Kafka/ClickHouse, but consumer path is single-record and lacks target-throughput evidence. | Add batch consumption/write path and metrics before load proof. |
| retry-dlq-target-readiness | KAFKA_CONTRACTS_OWNER | PARTIAL-FIX | `KafkaConsumerConfig.java`, `KafkaTopicConfig.java`, `docker-compose.yml`, `EmailFailedConsumer.java`, campaign/delivery retry scans | Shared Kafka DLQ now preserves source partition and defines six partitions in Java and Compose; retry scans, DLQ-specific observability, and production external Kafka topology evidence still need work before target-like load. | Add retry scan paging/sharding, DLQ volume/age/skew alerts, and release evidence proving production `kafka.dead-letter` partition/retention/replication settings. |

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
