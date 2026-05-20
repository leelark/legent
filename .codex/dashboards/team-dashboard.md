# Autonomous Team Dashboard

Generated: 2026-05-20T06:09:28.7283674Z

## Summary

- Registered module teams: 14
- Registered threads: 0
- Active threads: 0
- Active leases: 0
- Active worktrees: 0
- Ready work: 0
- Backlog work: 8
- Blocked work: 2
- Done work: 14

## Threads

| Thread | Role | Module | Status | Heartbeat | Stale | Next Action |
|---|---|---|---|---|---|---|

## Next Work

No unblocked READY work. Run pending-scan, research-pass, and refine-backlog.

## Blocked

- $(@{id=production-evidence-pack; title=Collect strict production release evidence; status=BLOCKED; priorityScore=64; scoring=; owner=RELEASE_MANAGER; partnerAgents=System.Object[]; scope=Collect real target evidence for release promotion.; nonGoals=Do not replace production evidence with local-only checks.; sourceFiles=System.Object[]; acceptanceCriteria=System.Object[]; validationProfile=release; validationCommands=System.Object[]; dependencies=System.Object[]; blockers=System.Object[]; memoryTargets=System.Object[]; nextAction=Collect external evidence, then run strict release gate.; lastUpdated=2026-05-20}.id): Collect external evidence, then run strict release gate.
- $(@{id=live-high-volume-proof; title=Collect target-like high-volume send proof; status=BLOCKED; priorityScore=62; scoring=; owner=PERFORMANCE_ENGINEER; partnerAgents=System.Object[]; scope=Prove 10 lakh send readiness with warmed sender/provider capacity, load evidence, Kafka/DB/Redis/ClickHouse metrics, retry/DLQ, and observability.; nonGoals=Do not claim throughput for new or unwarmed domains.; sourceFiles=System.Object[]; acceptanceCriteria=System.Object[]; validationProfile=performance; validationCommands=System.Object[]; dependencies=System.Object[]; blockers=System.Object[]; memoryTargets=System.Object[]; nextAction=Run a target-like load harness with provider-approved capacity before making throughput claims.; lastUpdated=2026-05-20}.id): Run a target-like load harness with provider-approved capacity before making throughput claims.
