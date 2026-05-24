# Production Hardening Runbook

Public multi-tenant operation requires target-environment evidence before production promotion. Use this runbook for local alert-response references and keep incident evidence in dated reports or external incident systems.

## Local Monitoring Handoff Validation

Local validation checks that each Prometheus alert has a severity, team owner, Alertmanager team route, runbook anchor, and Grafana dashboard panel reference. This is manifest hygiene only; it does not prove that target alerts fired, Alertmanager delivered notifications, Grafana loaded live data, or production monitoring handoff is complete.

Run:
- `powershell -ExecutionPolicy Bypass -File scripts\ops\validate-production-overlay.ps1`

Expected local artifacts:
- Alert rules: `infrastructure/kubernetes/observability/prometheus-alerts.yml`
- Alert routing: `infrastructure/kubernetes/observability/alertmanager.yml`
- Dashboard: `infrastructure/kubernetes/observability/grafana-legent-overview.json`

## Internal Service Identity

Local service-to-service HTTP calls still use `LEGENT_INTERNAL_API_TOKEN` and public-edge `/internal` deny rules. That control is now incrementally strengthened for the audience-resolution chunk read path: campaign-service must send `X-Internal-Service`, `X-Internal-Signature-Timestamp`, and `X-Internal-Signature` along with the existing internal credential. Audience-service validates the caller allowlist, tenant/workspace, action, job/chunk scope, and a short timestamp window before serving the chunk.

Triage:
- Confirm public access to `/internal` routes is still denied by Nginx and Kubernetes ingress via `scripts/ops/validate-route-map.ps1`.
- Confirm the caller service name matches the route allowlist and the signature action matches the intended route scope.
- Treat repeated signature failures as possible clock skew, stale deploy, misrouted service traffic, or credential drift. Do not log raw credentials, signatures, or payload contents in incident notes.
- Remember that production NetworkPolicy still allows broad backend-to-backend traffic; this application-layer signature is not mTLS, SPIFFE, or platform identity proof.

Recovery:
- Fix clock synchronization, service configuration, or rollout skew before widening allowlists.
- Keep `X-Internal-Token` validation enabled while signed headers roll out; do not remove edge denies.
- Broaden signed service identity to other internal routes only with route-specific allowlists and tests.
- Do not claim production service identity hardening is complete until target mTLS/service identity, NetworkPolicy, and signed-route evidence are available.

## Service Errors

Alert: `LegentServiceHighErrorRate`

Triage:
- Identify the affected `application` label, release version, and recent deploy or configuration changes.
- Compare 5xx rate with dependency health, Kafka lag, database connection saturation, and gateway or ingress errors.
- Inspect structured logs for correlation IDs, tenant/workspace-safe error classes, and repeated failing routes.
- Check whether the error spike is isolated to one service, one route family, or one shared dependency.

Recovery:
- Roll back or pause the most recent change when the spike follows a deployment.
- If dependency pressure is the cause, preserve backpressure and retry/idempotency controls while reducing non-critical load.
- Keep incident evidence open until the 5xx rate stays below threshold for at least one full alert window.

## Service Latency

Alert: `LegentServiceP95LatencyHigh`

Triage:
- Identify the affected `application` label and the routes or jobs contributing to p95 latency.
- Compare latency with executor saturation, database query time, Kafka lag, provider API latency, cache health, and ingress metrics.
- Check whether long-running imports, sends, tracking ingestion, or webhook retries are competing with interactive workspace traffic.

Recovery:
- Reduce or pause non-critical batch work where product-safe before increasing throughput.
- Scale only after confirming the bottleneck is service capacity rather than a saturated dependency.
- Record the latency window, affected workflows, mitigation, and remaining capacity evidence gap.

## Kafka Consumer Lag

Alert: `LegentKafkaConsumerLagHigh`

Triage:
- Identify the topic, consumer group, partition skew, and affected service deployment.
- Compare lag with consumer errors, DLQ volume, database write latency, ClickHouse insert latency, provider throttling, and executor saturation.
- Check whether retries are preserving idempotency and whether any consumer is repeatedly failing the same record or batch.

Recovery:
- Fix poison-pill or schema problems before increasing concurrency.
- Scale consumers only when downstream stores and provider/rate controls can absorb the extra work.
- Keep lag, DLQ, retry, and downstream saturation evidence in the incident record.

## Pod Restarts

Alert: `LegentPodRestarting`

Triage:
- Identify the affected namespace, pod, container, image, and rollout revision.
- Check recent deploys, liveness/readiness failures, OOM kills, crash loops, and node pressure.
- Review application logs with the current correlation ID window and compare restart timing with dependency health.
- If restarts follow a rollout, pause further promotion, roll back to the last healthy revision, and capture the rollout transcript.

Recovery:
- Restore the last known-good deployment or scale out only after confirming the failure is capacity-related.
- Keep the incident open until pod restarts stop for at least one alert window and readiness remains stable.
- Record root cause, impacted services, rollback action, and follow-up prevention work.

## Executor Saturation

Alerts: `LegentExecutorSaturationHigh`, `LegentExecutorSaturationCritical`

Triage:
- Identify the affected `application` and `executor` labels.
- Compare saturation with request latency, Kafka lag, send-job queue depth, import activity, tracking ingress, and webhook retries.
- Check whether saturation is bounded to one service or caused by a shared dependency.
- For send or tracking paths, preserve backpressure and rate controls before increasing throughput.

Recovery:
- Reduce non-critical load, pause scheduled jobs where product-safe, or scale the affected deployment when capacity is the confirmed cause.
- Do not bypass suppression, warmup, sender reputation, retry, or idempotency controls to drain work faster.
- Keep target metrics, queue lag, and user-visible impact in the incident record.

## Tracing

Alert: `LegentOtlpCollectorDown`

Triage:
- Verify the collector pod status, service endpoints, scrape target state, and recent configuration changes.
- Check whether applications can still emit logs and metrics with correlation IDs while trace export is unavailable.
- Confirm that collector outage is isolated from request handling, Kafka processing, and delivery/tracking pipelines.

Recovery:
- Restart or roll back the collector only after capturing the failing configuration and pod events.
- If trace loss spans an incident window, mark the evidence gap in the incident report and rely on logs/metrics for reconstruction.
- Do not claim monitoring handoff evidence until trace export and alert routing are verified in the target environment.

## Outbox Backlog

Alerts: `LegentOutboxReadyDepthHigh`, `LegentOutboxReadyDepthCritical`, `LegentOutboxOldestReadyAgeHigh`, `LegentOutboxOldestReadyAgeCritical`

Metrics:
- `legent_outbox_ready_depth{queue="tracking"}` and `legent_outbox_ready_depth{queue="delivery_feedback"}` report ready events waiting for Kafka publication.
- `legent_outbox_oldest_ready_age_seconds{queue="tracking"}` and `legent_outbox_oldest_ready_age_seconds{queue="delivery_feedback"}` report the age of the oldest ready event.
- These metrics intentionally expose only the low-cardinality `queue` label. Do not add tenant, workspace, email, subscriber, message, campaign, or domain labels.

Triage:
- Identify whether the `queue` label is `tracking` or `delivery_feedback`, then compare ready depth with oldest-ready age to distinguish a short spike from a stuck publisher.
- Check service pod health, scheduler execution, publish error logs, Kafka broker availability, producer errors, and Kafka topic authorization.
- Compare backlog growth with Kafka consumer lag, database latency, executor saturation, ClickHouse pressure for tracking, and delivery provider feedback volume.
- Sample database rows by status and next-attempt time using tenant-safe operational access. Do not export recipient emails, payload JSON, or customer data into incident notes.
- If events remain in `PUBLISHING` past the publishing lease, verify whether workers are crashing mid-publish before considering a controlled retry reset.

Recovery:
- Restore Kafka connectivity, topic permissions, or the affected service deployment before manually changing outbox rows.
- Scale the affected service only after confirming the bottleneck is publisher capacity and downstream Kafka/database dependencies can absorb the drain rate.
- For poison payloads or schema failures, fix the publisher or event contract first; do not drop, bulk-publish, or mark events published without product-owner and incident-commander approval.
- Keep suppression, signed tracking, idempotency, and retry behavior intact while draining backlog.
- Close the incident only after ready depth returns near baseline, oldest-ready age stays below the warning threshold for one full alert window, and failed/exhausted events have an owner.

## Retry and DLQ Backlog

Alerts: `LegentRetryReadyDepthHigh`, `LegentRetryOldestReadyAgeHigh`, `LegentDlqDepthHigh`, `LegentDlqOldestAgeHigh`, `LegentDlqSkewHigh`

Metrics:
- `legent_retry_ready_depth{queue=...}` reports ready retry backlog for campaign partial batches, campaign stale processing batches, and delivery scheduled messages.
- `legent_retry_oldest_ready_age_seconds{queue=...}` reports the age of the oldest ready retry item.
- `legent_dlq_depth{source=...}` reports terminal/open DLQ-style backlog for campaign dead letters and delivery failed messages.
- `legent_dlq_oldest_age_seconds{source=...}` reports the oldest DLQ-style backlog age.
- `legent_dlq_skew_ratio{source=...}` reports how concentrated DLQ backlog is in the largest job. These metrics intentionally expose only `queue` or `source`.

Triage:
- Identify the `queue` or `source` label, then compare depth, oldest age, Kafka lag, outbox backlog, database latency, provider throttling, and executor saturation.
- For campaign partial/stale-processing queues, confirm atomic retry claims are moving batches without duplicate batch-created events.
- For delivery scheduled retries, confirm retry content references are resolvable before increasing retry drain.
- For DLQ skew, inspect whether one job or campaign is poisoning retries. Keep incident notes tenant-safe and do not export raw payloads, emails, or subscriber IDs.
- Confirm production Kafka evidence remains current: broker count, AZs, replication factor, min ISR, `acks=all`, disabled auto-topic creation, topic retention, under-replicated/offline partitions, consumer lag, and alert routing.

Recovery:
- Fix poison payload, schema, content-reference, provider, or policy failures before raising concurrency.
- Replay or ignore DLQ entries only through documented operator workflows with product-owner and incident-commander approval.
- Scale consumers only after downstream stores, provider limits, warmup, suppression, unsubscribe, and idempotency controls are confirmed healthy.
- Close the incident after retry/DLQ depth and oldest age return near baseline for one full alert window and skew has an owner.
