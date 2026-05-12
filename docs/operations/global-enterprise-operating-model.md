# Global Enterprise Operating Model

Date: 2026-05-12

## Scope

Phase 5 adds global operating primitives without hardcoding a cloud provider. The runtime defaults to active-warm and fail-closed regional movement. Active-active is available only when the operating model and tenant residency policy both allow it.

## Kustomize Overlays

Render the portable examples before applying them:

```powershell
kubectl kustomize infrastructure/kubernetes/overlays/global/global-primary
kubectl kustomize infrastructure/kubernetes/overlays/global/global-standby
kubectl kustomize infrastructure/kubernetes/overlays/global/global-active-active
```

The overlays layer on top of `overlays/production` and add:

- region and topology labels
- topology spread constraints
- pod anti-affinity
- region-scoped config map
- external secret references for KMS, replication, and marketplace keys

## Failover Drill

1. Confirm `Global Ops` has an operating model, residency policy, encryption policy, and evidence pack.
2. Evaluate the proposed movement:

```powershell
Invoke-RestMethod -Method Post `
  -Uri https://api.legent.example/api/v1/global/failover/evaluate `
  -Headers @{ "X-Tenant-Id" = "<tenant>"; "X-Workspace-Id" = "<workspace>" } `
  -Body (@{
    operatingModelKey = "global-active-warm-default"
    dataClass = "PROFILE"
    sourceRegion = "us-east-1"
    targetRegion = "us-west-2"
  } | ConvertTo-Json) `
  -ContentType "application/json"
```

3. Run backup and restore proof for PostgreSQL, ClickHouse, and MinIO.
4. Promote traffic only after the evaluation allows movement and the latest restore evidence meets RPO/RTO.
5. Create a failover drill record with planned and actual RPO/RTO, affected services, findings, and evidence refs.
6. Attach the drill output to an evidence pack.

## Evidence To Capture

- rendered Kustomize output checksum
- operating model key and topology mode
- tenant residency policy key and data class
- failover evaluation response
- backup artifact URI and restore transcript
- synthetic smoke result
- actual RPO and RTO
- operator, approver, start time, completion time, and pass/fail verdict

## Guardrails

- Missing residency policy blocks failover.
- Blocked target region blocks failover even in active-active.
- Active-warm targets must be configured standby regions.
- Active-active targets must be configured active regions.
- Marketplace connector jobs stay dry-run unless a tenant credential reference exists.
- Autonomous optimization defaults to `SUGGEST_ONLY`; auto-apply requires passing policy simulation, brand/compliance guardrails, approval record, applied snapshot, and rollback snapshot.

## Limitations

The repository can render the global overlays and record control-plane evidence. Real RPO/RTO proof still requires a live stack with production-like data, replication, DNS/traffic control, object storage, and tenant credentials.
