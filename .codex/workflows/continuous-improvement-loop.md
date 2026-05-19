# Continuous Improvement Loop

Fresh baseline: 2026-05-20.

This workflow makes autonomous work repeatable without retaining old memory.

## Loop

1. **Validate operating system**: run `.codex/utilities/validate-codex-system.ps1`.
2. **Recover current work**: inspect active work, real checkpoints, active leases, and active worktrees.
3. **Audit**: scan implementation, route ownership, infra, CI, tests, docs, risks, and TODOs.
4. **Research**: verify current product, security, provider, dependency, or compliance facts when claims may change.
5. **Refine**: convert findings into structured work items with priority score, owner, acceptance criteria, validation profile, dependencies, blockers, and memory targets.
6. **Select**: choose the highest-score `READY` item that is safe to work locally.
7. **Assign**: use routing matrix and, when possible, keep up to six independent subagents active with disjoint ownership.
8. **Checkpoint**: create or update a real checkpoint before edits.
9. **Implement**: make the smallest coherent production-quality change.
10. **Validate**: run the required gates and Codex system validator.
11. **Record**: update memory, backlog queue, checkpoint, reports, worktree registry, and leases.
12. **Repeat**: continue until all safe local work is done or blocked.

## Selection Policy

Use `.codex/backlog/queue.json` as the durable queue. Sort `READY` items by:

1. highest `priorityScore`,
2. no blockers,
3. smallest coherent safe scope,
4. strongest validation availability,
5. lowest conflict with active leases/worktrees.

## Research Policy

Use current official sources for external facts. Store concise dated summaries in `docs/product/competitor-research/` or `.codex/reports/`, then turn actionable gaps into backlog items.

## Evidence Policy

Do not claim production readiness, Salesforce parity, guaranteed inbox placement, or 10 lakh sends in 10 hours without evidence. Missing external evidence keeps work `BLOCKED`.
