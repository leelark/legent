# Production Hardening Runbook

Public multi-tenant operation requires target-environment evidence before production promotion. Use this runbook for local alert-response references and keep incident evidence in dated reports or external incident systems.

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
