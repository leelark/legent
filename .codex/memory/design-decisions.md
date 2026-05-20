# Design Decisions

Fresh baseline date: 2026-05-20.

Current decisions:
- `.codex` is the authoritative autonomous organization layer for Codex sessions in this repository.
- `.codex/bootstrap.md` is the default entry point for non-trivial work.
- `.codex/memory` is a fresh current-state baseline. Earlier memory entries were cleared at user request.
- Continuous work follows audit, refine, pending scan, research, score, implement, validate, record, and repeat.
- Parallel execution is capped at 6 active subagents when delegation is available and independent ownership exists.
- Production and high-volume claims require evidence; local validators cannot substitute for target-environment proof.

## 2026-05-20 Parity Research Contract

Source: `docs/product/competitor-research/README.md`, `.codex/workflows/salesforce-parity-roadmap.md`, `docs/product/salesforce-parity-matrix.md`.

Decision: parity research must use dated source IDs, a source-register note, fact/inference separation, consistent matrix statuses, and queue-backed gap IDs.

Rationale: broad claims like "Salesforce parity" are unsafe unless current external facts and current local source evidence are linked.

Impact: every future parity refresh should update `docs/product/competitor-research/` first, cite source IDs from the matrix, and route implementable gaps through `.codex/backlog/queue.json`.

Follow-up: keep source URLs current before product copy or roadmap claims.

## 2026-05-20 AI Claim Boundary

Source: local audit of foundation intelligence controllers/services and source search for model-provider integrations.

Decision: deterministic scoring, heuristics, and rules-based operations assistance are not true model-backed AI parity.

Rationale: current source evidence did not prove OpenAI, Anthropic, embedding, completion, or other model-provider integration in services/frontend. AI assistance, STO, predictive segments, and generative content require explicit governance, provider disclosure, data-use controls, opt-in, audit, and human review before claims.

Impact: use `ai-governance-optimization-foundation` before marking model-backed AI feature work READY.

Follow-up: do not claim Salesforce Einstein parity until model-backed behavior, data governance, and validation evidence exist.

## 2026-05-20 UI Mode Boundary

Source: `frontend/src/stores/uiStore.ts`, `frontend/src/components/shell/Header.tsx`, `frontend/src/styles/globals.css`.

Decision: current UI modes are `BASIC` and `ADVANCED`; role-gated Admin is a future workflow contract, not a current third local toggle.

Rationale: the shell persists and toggles `BASIC`/`ADVANCED`, while current hiding is coarse and CSS-based. Admin behavior must be backed by authorization and route/control policy.

Impact: use `mode-aware-workflow-contract` before claiming beginner/advanced/admin parity.

Decision update rule: add only durable decisions with source, rationale, impact, and validation or follow-up.
