# Phase 3 High-Volume Load Test

Purpose: exercise enterprise-scale paths for imports, segmentation/query preview, sends, tracking ingest, and BI reporting before controlled enterprise rollout.

## Command

```powershell
.\scripts\load\phase3-high-volume-load.ps1 -BaseUrl http://localhost:8080/api/v1 -TenantId tenant-load -WorkspaceId workspace-load -Token $env:LEGENT_LOAD_TOKEN
```

Use `-DryRun` in CI to validate scenario wiring without generating traffic:

```powershell
.\scripts\load\phase3-high-volume-load.ps1 -DryRun
```

PowerShell 5.1 runs the harness sequentially. Use PowerShell 7 when parallel execution is required for real load.

## Gates

- Import p95 latency under agreed SLO at target volume.
- Segmentation preview returns bounded rows and never scans outside tenant/workspace.
- Send enqueue path maintains idempotency and does not overrun provider capacity profile.
- Tracking ingest preserves outbox and ClickHouse write health.
- BI report stays responsive against ClickHouse rollups.
- Error rate stays below 0.5% outside intentional throttling.

## Evidence To Capture

- Script JSON output.
- Gateway, service, Postgres, ClickHouse, Kafka, Redis, and Kubernetes metrics.
- Saturation alerts for async queues and provider throttles.
- Tenant isolation spot checks using at least two tenants and two workspaces.
- Rollback and restore notes if any limit is breached.
