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
  -LiveEvidencePath .\docs\operations\phase3-live-evidence-2026q2.json `
  -EvidenceOutputPath .\docs\operations\phase3-high-volume-load-evidence-2026q2.json `
  -Imports 0 `
  -Sends 1 `
  -RequireLive `
  -FailOnErrors
```

`-Imports 0` is required for this release-grade command because the current import scenario uses the local/test `POST /api/v1/imports/mock` endpoint. `-RequireLive` fails closed when an enabled scenario depends on that profile-bound mock route. Add a production-safe multipart import implementation before using the import scenario as live release evidence.

Live segmentation and tracking/send scenarios must use real seeded dataset IDs. With `-RequireLive`, the default `campaign-load` and `load-data-extension` placeholders are rejected when their scenarios are enabled. `-CampaignId` accepts comma-separated IDs or a PowerShell array. Use `-Sends 1` for one campaign where one trigger fans out to one seeded audience. If `-Sends` is greater than 1, provide at least that many unique campaign IDs; repeated live triggers against one campaign are not high-volume send evidence.

`-RequireLive` also requires `-LiveEvidencePath`. This file is a structured JSON operator evidence pack for target-only proof that the local harness cannot produce by itself. The harness rejects missing files, invalid JSON, placeholder/local/example values, secret-like fields, and failed threshold comparisons before generating traffic. Do not include authorization headers, tokens, cookies, private keys, credentials, customer data, or raw payloads.

```json
{
  "environment": "prod-like-staging-use1",
  "observedAt": "2026-05-16T12:30:00Z",
  "clickHouse": {
    "rawEventsTable": "legent_analytics.raw_events",
    "partitionKey": "toYYYYMM(timestamp)",
    "ttlExpression": "TTL timestamp + INTERVAL 180 DAY DELETE",
    "ttlDays": 180,
    "partitionCount": 6,
    "proofRef": "s3://legent-release-evidence/prod-like-staging-use1/clickhouse-raw-events-ttl.json"
  },
  "postgres": {
    "retentionFunction": "purge_expired_raw_events",
    "retentionDays": 30,
    "lastPurgeRows": 25000,
    "lastPurgeDurationMs": 842,
    "purgeProofRef": "s3://legent-release-evidence/prod-like-staging-use1/postgres-raw-event-purge.json"
  },
  "remoteRender": {
    "sampleCount": 2000,
    "p95Ms": 420,
    "maxP95Ms": 1500,
    "proofRef": "s3://legent-release-evidence/prod-like-staging-use1/content-render-latency.json"
  },
  "kafkaHandoff": {
    "topic": "email.send.requested",
    "consumerGroup": "delivery-service",
    "maxConsumerLag": 1250,
    "maxAllowedConsumerLag": 10000,
    "p95PublishLatencyMs": 65,
    "maxPublishLatencyMs": 500,
    "proofRef": "s3://legent-release-evidence/prod-like-staging-use1/kafka-handoff-pressure.json"
  },
  "deliveryProviderCapacity": {
    "profileName": "ses-prodlike-pool-a",
    "warmedDomains": 4,
    "approvedPerMinute": 60000,
    "plannedPeakPerMinute": 50000,
    "throttleState": "READY",
    "proofRef": "s3://legent-release-evidence/prod-like-staging-use1/provider-capacity.json"
  }
}
```

Live proof gates:

- ClickHouse must prove the `raw_events` monthly timestamp partition and timestamp-based delete TTL used by the tracking rollup schema.
- Postgres must prove the `purge_expired_raw_events` retention function, retention days, last bounded purge rows, and purge duration.
- Remote render proof must include sample count and p95 latency at or below its `maxP95Ms` gate.
- Kafka handoff proof must include topic, consumer group, max lag, max allowed lag, p95 publish latency, and publish latency gate.
- Delivery provider proof must include warmed domains, approved capacity per minute, planned peak per minute, and a non-blocked throttle state. Approved capacity must be at least the planned peak.

Use `-EvidenceOutputPath` to write the sanitized combined JSON report. The report includes route validation, scenario results, campaign send evidence, the structured live proof fields, and the output path; it does not include the bearer token or request headers.

Use `-DryRun` in CI to validate scenario wiring without generating traffic:

```powershell
.\scripts\load\phase3-high-volume-load.ps1 -DryRun
```

PowerShell 5.1 runs the harness sequentially. Use PowerShell 7 when parallel execution is required for real load. The JSON output records `schemaVersion`, `parallel`, `dataProfileName`, route validation warnings, live evidence proof status, evidence report path, per-scenario p95 latency, error rate, and pass/fail gate.

## Gates

- Import p95 latency under agreed SLO at target volume.
- Segmentation preview returns bounded rows and never scans outside tenant/workspace.
- Send enqueue path uses `POST /api/v1/campaigns/{id}/send`, maintains idempotency, and treats one trigger as one seeded campaign audience.
- Tracking ingest preserves outbox and ClickHouse write health.
- BI report stays responsive against ClickHouse rollups.
- Error rate stays below 0.5% outside intentional throttling.
- Live proof JSON passes the ClickHouse TTL/partition, Postgres purge, remote render latency, Kafka handoff pressure, and provider capacity gates.

## Evidence To Capture

- Script JSON output.
- Sanitized `-EvidenceOutputPath` JSON report.
- `-LiveEvidencePath` JSON pack with target proof references, not secrets.
- Gateway, service, Postgres, ClickHouse, Kafka, Redis, and Kubernetes metrics.
- Saturation alerts for async queues and provider throttles.
- Tenant isolation spot checks using at least two tenants and two workspaces.
- Rollback and restore notes if any limit is breached.
