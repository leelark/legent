# SLO And Alert Routing

Date: 2026-05-11

## Beta SLOs

- API availability: 99.5% monthly for authenticated API routes.
- API latency: p95 below 1.5 seconds for core workflows.
- Tracking ingestion: p95 accepted below 500 ms, no sustained outbox backlog above 10 minutes.
- Delivery orchestration: no provider queue saturation above 85% for more than 10 minutes.
- Restore: ClickHouse and PostgreSQL restore drill evidence within the last 30 days.

## Alerts

- `LegentServiceHighErrorRate`: warning at 5xx rate above 2% for 10 minutes.
- `LegentServiceP95LatencyHigh`: warning at p95 above 1.5 seconds for 10 minutes.
- `LegentKafkaConsumerLagHigh`: critical at lag above 10000 for 15 minutes.
- `LegentExecutorSaturationHigh`: warning above 85% for 10 minutes.
- `LegentExecutorSaturationCritical`: critical above 95% for 5 minutes.
- `LegentOtlpCollectorDown`: warning when trace export is unavailable.

## Routing

Alertmanager route config lives at:

- `infrastructure/kubernetes/observability/alertmanager.yml`

Critical alerts route to `platform-pager`. Warning alerts route to `platform-primary`.

## Evidence

Attach incident, drill, and release evidence to the audit folder or operations docs with:

- timestamp
- environment
- release version
- commands run
- pass/fail result
- owner
