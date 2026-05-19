# Unresolved Risks

Fresh baseline date: 2026-05-20.

| Priority | Area | Risk | Source | Next Action |
|---|---|---|---|---|
| P0 | Release | Production readiness cannot be claimed without target-environment evidence. | `AGENTS.md`, `.codex/state/team-state.json`, `scripts/ops/release-gate.ps1` | Collect strict evidence and run release gate without local-only mode. |
| P0 | Deliverability | Guaranteed inbox placement must never be claimed. | `AGENTS.md` | Keep product copy, docs, and prompts evidence-based. |
| P0 | High volume | 10 lakh sends in 10 hours requires warmed/authenticated senders, provider capacity, suppression discipline, shard-aware queues, rate control, and load evidence. | `AGENTS.md`, `.codex/memory/performance-bottlenecks.md` | Run high-volume readiness audit and measured load validation before claims. |
| P1 | Security | Tenant/workspace context, auth cookies, signed tracking, public endpoints, and Kafka trust are safety boundaries. | `AGENTS.md`, `.codex/memory/security-findings.md` | Add focused tests whenever these paths are touched. |
| P1 | Autonomy | Continuous work can safely continue only while checkpoints, state, validation, and memory stay current. | `.codex/bootstrap.md`, `.codex/workflows/continuous-improvement-loop.md` | Run `.codex/utilities/validate-codex-system.ps1` during each autonomous cycle. |
