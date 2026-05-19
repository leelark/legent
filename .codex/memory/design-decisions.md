# Design Decisions

Fresh baseline date: 2026-05-20.

Current decisions:
- `.codex` is the authoritative autonomous organization layer for Codex sessions in this repository.
- `.codex/bootstrap.md` is the default entry point for non-trivial work.
- `.codex/memory` is a fresh current-state baseline. Earlier memory entries were cleared at user request.
- Continuous work follows audit, refine, pending scan, research, score, implement, validate, record, and repeat.
- Parallel execution is capped at 6 active subagents when delegation is available and independent ownership exists.
- Production and high-volume claims require evidence; local validators cannot substitute for target-environment proof.

Decision update rule: add only durable decisions with source, rationale, impact, and validation or follow-up.
