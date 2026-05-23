# Autonomous Team Dashboard

Generated: 2026-05-23T21:38:58.4838825Z

## Summary

- Registered module teams: 15
- Registered threads: 18
- Active threads: 1
- Active leases: 0
- Active worktrees: 0
- Ready work: 0
- Backlog work: 19
- Blocked work: 16
- Done work: 188

## Threads

| Thread | Role | Module | Status | Heartbeat | Stale | Next Action |
|---|---|---|---|---|---|---|
| overall-24x7 | OVERALL | overall | ARCHIVED | 2026-05-20T11:52:30.5885789Z | True | Archived stale safe-stopped coordinator; superseded by multi-module-coordinator-20260520T113233Z. |
| deliverability-service-20260520T100626Z | MODULE | deliverability-service | ARCHIVED | 2026-05-20T10:21:30.3925579Z | True | Archived duplicate scout registration; active module ownership is coordinated through existing frontend/foundation threads. |
| content-service-20260520T100625Z | MODULE | content-service | ARCHIVED | 2026-05-20T10:21:30.6208978Z | True | Archived duplicate scout registration; active module ownership is coordinated through existing frontend/foundation threads. |
| audience-service-20260520T100624Z | MODULE | audience-service | ARCHIVED | 2026-05-20T10:21:30.6609071Z | True | Archived duplicate scout registration; active module ownership is coordinated through existing frontend/foundation threads. |
| automation-service-20260520T100624Z | MODULE | automation-service | ARCHIVED | 2026-05-20T10:21:30.6758853Z | True | Archived duplicate scout registration; active module ownership is coordinated through existing frontend/foundation threads. |
| tracking-service-20260520T100626Z | MODULE | tracking-service | ARCHIVED | 2026-05-20T10:21:30.7094825Z | True | Archived duplicate scout registration; active module ownership is coordinated through existing frontend/foundation threads. |
| frontend-20260520T100626Z | MODULE | frontend | ARCHIVED | 2026-05-20T10:21:30.7528164Z | True | Archived duplicate frontend registration; frontend-20260520T100638Z is active. |
| frontend-20260520T100638Z | MODULE | frontend | ARCHIVED | 2026-05-20T11:33:45.7207624Z | True | Archived safe-stopped frontend module thread before frontend-20260520T113115Z starts. |
| foundation-service-20260520T100717Z | MODULE | foundation-service | PAUSED | 2026-05-20T13:21:41.7830523Z | True | Safe-stopped by user request after completing foundation-service scoped fixes and final reassessment. Resume from .codex/checkpoints/20260520T131300Z-foundation-service-safe-stop.json and foundation handoff. |
| multi-module-coordinator-20260520T113233Z | OVERALL | multi-module-coordinator | PAUSED | 2026-05-20T13:24:50.3857224Z | True | Superseded by ONE_OVERALL_TEAM coordinator overall-20260520T133124Z for the current run. |
| frontend-20260520T113115Z | MODULE | frontend | ARCHIVED | 2026-05-20T13:14:17.9511942Z | True | Safe-stopped by user request after completing template-studio-mode-contract; frontend heartbeat automation deleted. |
| audience-service-20260520T121322Z | MODULE | audience-service | PAUSED | 2026-05-20T13:16:16.7756807Z | True | Safe stop requested by user after completing predictive-segments-governance and contact-data-designer-preview-governance. Resume from checkpoint 20260520T125000Z-contact-data-designer-preview-governance for additive Contact Builder provenance/classification/audit tables, controller API validation, frontend relationship-designer controls, or internal endpoint security-chain tests. |
| overall-20260520T133124Z | OVERALL | overall | PAUSED | 2026-05-20T17:13:37.3491151Z | True | Safe-stopped by user request after completing email-governance-policy-objects. Resume from .codex/checkpoints/20260520T164135Z-email-governance-policy-objects.json and .codex/threads/overall-20260520T133124Z-handoff.md. |
| overall-20260520T181712Z | OVERALL | overall | PAUSED | 2026-05-20T22:57:19.4891311Z | True | Safe-stopped by user request. tracking-ingestion-batch-consumer-readiness is locally validated but BLOCKED on Docker/PostgreSQL and ClickHouse dedupe/reconciliation evidence; no active agents or leases. |
| overall-20260521T210913Z | OVERALL | overall | PAUSED | 2026-05-22T03:51:12.9629255Z | True | Safe-stopped per user request after completing latest-audit-safe-local-followups-20260522; resume only on new user direction. |
| overall-20260522T121051Z | OVERALL | overall | PAUSED | 2026-05-22T21:45:41.5721230Z | True | Safe-stopped after completing all unblocked local work from the latest audit. Resume only on new user direction or after blocked evidence and policy decisions are available. |
| overall-20260523T094000Z | OVERALL | overall | PAUSED | 2026-05-23T10:40:31.5242917Z | True | Safe-stopped after completing all unblocked local work; resume from .codex/threads/overall-20260523T094000Z-handoff.md or after blocked evidence/policy decisions are available. |
| overall-20260523T104219Z | OVERALL | overall | ACTIVE | 2026-05-23T21:12:02.4574207Z | False | Start overall autonomous loop. |

## Next Work

No unblocked READY work. Run pending-scan, research-pass, and refine-backlog.

## Blocked

- `delivery-policy-provider-egress-proof`: External provider/domain evidence missing
- `ga-smoke-restore-monitoring-admission-evidence`: External GA evidence not present
- `production-evidence-pack`: Collect external evidence, then run strict release gate.
- `live-high-volume-proof`: Run a target-like load harness with provider-approved capacity before making throughput claims.
- `production-image-digest-provenance-evidence`: External image evidence not present
- `production-egress-policy-evidence`: External reviewed egress evidence not present
- `automation-target-runtime-replay-evidence`: Target replay evidence missing
- `tracking-ingestion-batch-consumer-readiness`: Collect Docker/PostgreSQL and live ClickHouse evidence for idempotency, ambiguous/partial batch writes, physical raw-event duplicate behavior, and downstream reconciliation before marking DONE or making BI/throughput claims.
- `delivery-policy-legal-evidence-pack`: Human compliance review evidence missing
- `contact-sendable-key-migration-proof`: Target Flyway/data migration evidence missing
