# Production Hardening Runbook

Date: 2026-05-11

## Release Gate

Run before controlled beta promotion:

```powershell
.\scripts\ops\release-gate.ps1 -RunSyntheticSmoke -SmokeBaseUrl https://api.legent.example
```

Expected gates:

- Backend Maven tests pass.
- Frontend lint, production build, and Playwright smoke pass.
- Docker Compose config renders.
- Synthetic API smoke passes against the target environment.
- Production Kustomize overlay renders.
- Production overlay deletes broad base egress, renders default deny, and only permits reviewed egress. Current repo overlay allows same-namespace pod egress plus DNS; target clusters still need reviewed managed-service/provider CIDR or FQDN policy overlays before real promotion.

## Actuator And Internal Endpoints

Allowed without application auth:

- `/actuator/health`
- `/actuator/health/**`
- `/actuator/prometheus`

All other `/actuator/**` endpoints require JWT role `PLATFORM_ADMIN`. Public ingress also returns `404` for `/actuator/*`, so actuator access must come from cluster networking or an approved admin path.

Checks:

```powershell
Invoke-WebRequest https://api.legent.example/actuator/env -UseBasicParsing
kubectl -n legent exec deploy/identity-service -- wget -qO- http://127.0.0.1:8089/actuator/health
```

## Executor Saturation

Metric:

- `legent_executor_saturation_ratio{application,executor}`

Alerts:

- Warning above `0.85` for 10 minutes.
- Critical above `0.95` for 5 minutes.

Triage:

1. Check queue growth and active threads for the named executor.
2. Inspect upstream request rate and Kafka/outbox backlog.
3. Scale the owning service if CPU and downstreams are healthy.
4. If saturation is caused by downstream slowness, apply provider throttles or pause scheduled jobs before raising pool sizes.

## Tracing

Services export Micrometer/OpenTelemetry traces to `http://otel-collector:4318/v1/traces`. Request and correlation IDs are emitted on responses as `X-Request-Id` and `X-Correlation-Id`.

Triage:

1. Confirm `otel-collector` replicas are ready.
2. Confirm `MANAGEMENT_OTLP_TRACING_ENDPOINT` is set in `legent-config`.
3. Search logs by `correlationId`.
4. Use traces to join ingress, service, database, Kafka, and outbound provider latency.

## Rollback

```powershell
kubectl -n legent rollout history deployment/identity-service
kubectl -n legent rollout undo deployment/identity-service --to-revision=<revision>
kubectl -n legent rollout status deployment/identity-service --timeout=180s
```

Repeat per service. Production overlay keeps `revisionHistoryLimit: 10` and rolling updates use `maxUnavailable: 0`.
