# Autonomous Team Dashboard

Generated: 2026-05-20T10:22:02.1027387Z

## Summary

- Registered module teams: 14
- Registered threads: 9
- Active threads: 3
- Active leases: 3
- Active worktrees: 0
- Ready work: 0
- Backlog work: 8
- Blocked work: 3
- Done work: 27

## Threads

| Thread | Role | Module | Status | Heartbeat | Stale | Next Action |
|---|---|---|---|---|---|---|
| overall-24x7 | OVERALL | overall | ACTIVE | 2026-05-20T10:21:45.8915984Z | False | Coordinate active frontend and foundation module threads; monitor leases/checkpoints/handoffs and avoid duplicate module work. |
| deliverability-service-20260520T100626Z | MODULE | deliverability-service | ARCHIVED | 2026-05-20T10:21:30.3925579Z | False | Archived duplicate scout registration; active module ownership is coordinated through existing frontend/foundation threads. |
| content-service-20260520T100625Z | MODULE | content-service | ARCHIVED | 2026-05-20T10:21:30.6208978Z | False | Archived duplicate scout registration; active module ownership is coordinated through existing frontend/foundation threads. |
| audience-service-20260520T100624Z | MODULE | audience-service | ARCHIVED | 2026-05-20T10:21:30.6609071Z | False | Archived duplicate scout registration; active module ownership is coordinated through existing frontend/foundation threads. |
| automation-service-20260520T100624Z | MODULE | automation-service | ARCHIVED | 2026-05-20T10:21:30.6758853Z | False | Archived duplicate scout registration; active module ownership is coordinated through existing frontend/foundation threads. |
| tracking-service-20260520T100626Z | MODULE | tracking-service | ARCHIVED | 2026-05-20T10:21:30.7094825Z | False | Archived duplicate scout registration; active module ownership is coordinated through existing frontend/foundation threads. |
| frontend-20260520T100626Z | MODULE | frontend | ARCHIVED | 2026-05-20T10:21:30.7528164Z | False | Archived duplicate frontend registration; frontend-20260520T100638Z is active. |
| frontend-20260520T100638Z | MODULE | frontend | ACTIVE | 2026-05-20T10:20:57.1541895Z | False | Read-only frontend mode audits running; prepare exact write lease for mode-aware workflow contract. |
| foundation-service-20260520T100717Z | MODULE | foundation-service | ACTIVE | 2026-05-20T10:21:36.6071585Z | False | Foundation audit identified forward migration candidate; waiting for coordination checkpoint lease to clear before edits. |

## Next Work

No unblocked READY work. Run pending-scan, research-pass, and refine-backlog.

## Blocked

- $(@{id=production-evidence-pack; title=Collect strict production release evidence; status=BLOCKED; priorityScore=64; scoring=; owner=RELEASE_MANAGER; partnerAgents=System.Object[]; scope=Collect real target evidence for release promotion.; nonGoals=Do not replace production evidence with local-only checks.; sourceFiles=System.Object[]; acceptanceCriteria=System.Object[]; validationProfile=release; validationCommands=System.Object[]; dependencies=System.Object[]; blockers=System.Object[]; memoryTargets=System.Object[]; nextAction=Collect external evidence, then run strict release gate.; lastUpdated=2026-05-20}.id): Collect external evidence, then run strict release gate.
- $(@{id=live-high-volume-proof; title=Collect target-like high-volume send proof; status=BLOCKED; priorityScore=62; scoring=; owner=PERFORMANCE_ENGINEER; partnerAgents=System.Object[]; scope=Prove 10 lakh send readiness with warmed sender/provider capacity, load evidence, Kafka/DB/Redis/ClickHouse metrics, retry/DLQ, and observability.; nonGoals=Do not claim throughput for new or unwarmed domains.; sourceFiles=System.Object[]; acceptanceCriteria=System.Object[]; validationProfile=performance; validationCommands=System.Object[]; dependencies=System.Object[]; blockers=System.Object[]; memoryTargets=System.Object[]; nextAction=Run a target-like load harness with provider-approved capacity before making throughput claims.; lastUpdated=2026-05-20}.id): Run a target-like load harness with provider-approved capacity before making throughput claims.
- $(@{id=campaign-audience-eligibility-final-gate; title=Add final suppression and preference gate before campaign delivery handoff; status=BLOCKED; priorityScore=71; scoring=; owner=CAMPAIGN_SERVICE_OWNER; partnerAgents=System.Object[]; scope=Ensure direct and legacy campaign send paths cannot hand off recipients to delivery without authoritative preference and suppression checks, including local audience suppressions and nested email channel preferences.; nonGoals=Do not bypass warmup, rate controls, unsubscribe policy, or provider inbox safety.; sourceFiles=System.Object[]; acceptanceCriteria=System.Object[]; validationProfile=backend-focused; validationCommands=System.Object[]; dependencies=System.Object[]; blockers=System.Object[]; memoryTargets=System.Object[]; nextAction=Make a compatibility decision for legacy campaign recipient payloads, then implement the eligibility marker contract if approved.; lastUpdated=2026-05-20T07:53:21.2501415Z; startedAt=2026-05-20T07:41:26.3257305Z; completedAt=2026-05-20T07:53:21.2501415Z; outcome=Audience-service final eligibility first slice is complete and validated, but the broader parent cannot be marked DONE until a compatibility decision is made for existing campaign legacy recipient JSON payloads. The proposed next slice is an eligibility marker/contract for BatchingService row payloads and SendExecutionService legacy fallback behavior.; validationRun=System.Object[]; residualRisks=System.Object[]}.id): Make a compatibility decision for legacy campaign recipient payloads, then implement the eligibility marker contract if approved.
