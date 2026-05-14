# Phase 3 High-Volume Load Test

Purpose: exercise enterprise-scale paths for imports, segmentation/query preview, sends, tracking ingest, and BI reporting before controlled enterprise rollout.

The harness validates every enabled scenario against `config/gateway/route-map.json` and the owning service controller source before generating traffic. `-RequireLive` also runs `scripts/ops/validate-route-map.ps1`, so route-map, Nginx, rendered ingress precedence, and controller roots must be synchronized before live requests are sent. Live runs must enable at least one live-supported scenario.

## Command

```powershell
.\scripts\load\phase3-high-volume-load.ps1 -BaseUrl http://localhost:8080/api/v1 -TenantId tenant-load -WorkspaceId workspace-load -Token $env:LEGENT_LOAD_TOKEN
```

For a release-grade run, point at a live stack, pass a real operator token, and name the production-like dataset. The harness fails before traffic starts when these preconditions are missing:

```powershell
.\scripts\load\phase3-high-volume-load.ps1 `
  -BaseUrl https://gateway.example.com/api/v1 `
  -TenantId tenant-enterprise-load `
  -WorkspaceId workspace-enterprise-load `
  -Token $env:LEGENT_LOAD_TOKEN `
  -DataProfileName enterprise-2026q2-volume `
  -DataExtensionId de-enterprise-load-2026q2 `
  -CampaignId campaign-enterprise-load-wave-001 `
  -Imports 0 `
  -Sends 1 `
  -RequireLive `
  -FailOnErrors
```

`-Imports 0` is required for this release-grade command because the current import scenario uses the local/test `POST /api/v1/imports/mock` endpoint. `-RequireLive` fails closed when an enabled scenario depends on that profile-bound mock route. Add a production-safe multipart import implementation before using the import scenario as live release evidence.

Live segmentation and tracking/send scenarios must use real seeded dataset IDs. With `-RequireLive`, the default `campaign-load` and `load-data-extension` placeholders are rejected when their scenarios are enabled. `-CampaignId` accepts comma-separated IDs or a PowerShell array. Use `-Sends 1` for one campaign where one trigger fans out to one seeded audience. If `-Sends` is greater than 1, provide at least that many unique campaign IDs; repeated live triggers against one campaign are not high-volume send evidence.

Use `-DryRun` in CI to validate scenario wiring without generating traffic:

```powershell
.\scripts\load\phase3-high-volume-load.ps1 -DryRun
```

PowerShell 5.1 runs the harness sequentially. Use PowerShell 7 when parallel execution is required for real load. The JSON output records `parallel`, `dataProfileName`, route validation warnings, per-scenario p95 latency, error rate, and pass/fail gate.

## Gates

- Import p95 latency under agreed SLO at target volume.
- Segmentation preview returns bounded rows and never scans outside tenant/workspace.
- Send enqueue path uses `POST /api/v1/campaigns/{id}/send`, maintains idempotency, and treats one trigger as one seeded campaign audience.
- Tracking ingest preserves outbox and ClickHouse write health.
- BI report stays responsive against ClickHouse rollups.
- Error rate stays below 0.5% outside intentional throttling.

## Evidence To Capture

- Script JSON output.
- Gateway, service, Postgres, ClickHouse, Kafka, Redis, and Kubernetes metrics.
- Saturation alerts for async queues and provider throttles.
- Tenant isolation spot checks using at least two tenants and two workspaces.
- Rollback and restore notes if any limit is breached.
