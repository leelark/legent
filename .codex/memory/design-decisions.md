# Design Decisions

Fresh baseline date: 2026-05-20.

Current decisions:
- `.codex` is the authoritative autonomous organization layer for Codex sessions in this repository.
- `.codex/bootstrap.md` is the default entry point for non-trivial work.
- `.codex/memory` is a fresh current-state baseline. Earlier memory entries were cleared at user request.
- Continuous work follows audit, refine, pending scan, research, score, implement, validate, record, and repeat.
- Parallel execution is capped at 6 active subagents when delegation is available and independent ownership exists.
- Autonomous operation supports both one overall coordinator thread and multiple module-level threads coordinated by thread registry, module team registry, leases, checkpoints, heartbeats, and dashboards.
- Memory files stay compact; detailed 24x7 activity is preserved in audit JSONL, checkpoints, reports, and backlog history.
- Production and high-volume claims require evidence; local validators cannot substitute for target-environment proof.

## 2026-05-20 Parity Research Contract

Source: `docs/product/competitor-research/README.md`, `.codex/workflows/salesforce-parity-roadmap.md`, `docs/product/salesforce-parity-matrix.md`.

Decision: parity research must use dated source IDs, a source-register note, fact/inference separation, consistent matrix statuses, and queue-backed gap IDs.

Rationale: broad claims like "Salesforce parity" are unsafe unless current external facts and current local source evidence are linked.

Impact: every future parity refresh should update `docs/product/competitor-research/` first, cite source IDs from the matrix, and route implementable gaps through `.codex/backlog/queue.json`.

Follow-up: keep source URLs current before product copy or roadmap claims.

## 2026-05-20 AI Claim Boundary

Source: local audit of foundation intelligence controllers/services, source search for model-provider integrations, `docs/product/ai-governance-optimization-foundation.md`, and current official vendor AI trust/optimization documentation.

Decision: deterministic scoring, heuristics, and rules-based operations assistance are not true model-backed AI parity. Model-backed content assistance, send-time optimization, predictive segments, frequency optimization, decisioning, or autonomous action require a feature-specific tenant/workspace policy before implementation.

Rationale: current source evidence did not prove OpenAI, Anthropic, embedding, completion, or other model-provider integration in services/frontend. Current operations assistance stores telemetry/evidence JSON and computes rules-based recommendations, so model-backed features need data classification, provider disclosure, masking/minimization, retention, opt-in/out, metering, audit, minimum data thresholds, confidence/fallback behavior, and human review before claims or provider integration.

Impact: use the claim taxonomy in `docs/product/ai-governance-optimization-foundation.md`: deterministic heuristic, predictive model, generative model, decisioning, autonomous action, and evidence-required. Do not claim Salesforce Einstein parity, AI parity, or autonomous optimization until the relevant follow-up slice has implementation and validation evidence.

Follow-up: implement governed slices for `ai-content-assistance-governance`, `send-time-optimization-governance`, `predictive-segments-governance`, and `frequency-optimization-governance` before product claims or provider integration.

## 2026-05-20 UI Mode Boundary

Source: `frontend/src/lib/ui-mode-contract.ts`, `frontend/src/stores/uiStore.ts`, `frontend/src/components/shell/Header.tsx`, `frontend/src/components/shell/Sidebar.tsx`, `frontend/src/app/(workspace)/campaigns/new/page.tsx`, `frontend/tests/e2e/ui-mode.spec.ts`, `frontend/tests/e2e/campaign-engine.spec.ts`.

Decision: `BASIC` and `ADVANCED` are the only local UI modes. Mode visibility is now modeled through typed frontend metadata in `ui-mode-contract.ts`; Admin remains role-gated through session roles and is not a third mode.

Rationale: CSS-only `data-advanced` hiding was not a durable workflow contract and direct route access should not be confused with authorization. Render-time filtering keeps advanced shell controls out of the focus order, while workflow submit paths must also guard hidden advanced payloads.

Impact: Settings navigation is filtered by mode metadata, Admin navigation remains role-gated, and the campaign wizard Experiment Engine renders/submits only in `ADVANCED` mode. Future workflow surfaces should use the same metadata helpers and keep backend authorization authoritative.

## 2026-05-20 Send-Time Optimization Boundary

Source: `ClosedLoopOptimizationService.java`, `OptimizationPerformanceServiceTest.java`, `docs/product/ai-governance-optimization-foundation.md`, and `docs/product/salesforce-parity-matrix.md`.

Decision: Legent may evaluate deterministic send-time optimization readiness, confidence, fallback, and safety gates through `SEND_TIME` policies, but this is not model-backed STO and must not change live campaign launch timing without later campaign/delivery runtime integration and evidence.

Rationale: Salesforce-level STO implies trained prediction behavior and low-data fallback, while current Legent source has deterministic intelligence only. A policy-evaluation contract gives product and engineering a safe boundary before introducing scheduling changes or model providers.

Impact: launch-time changes require human approval, rollback evidence, and quiet-hours, approval, suppression, warmup, rate-limit, provider-capacity, and deliverability gates. Low-data inputs must report fallback and low confidence rather than personalized timing certainty.

Follow-up: implement campaign/delivery scheduling integration, timezone proof, model/data provenance, and target data-quality evidence before any STO parity or production claim.

## 2026-05-20 Multi-Module Coordinator Boundary

Source: `.codex/prompts/multi-module-coordinator-24x7.md`, `.codex/workflows/multi-thread-autonomous-teams.md`, `.codex/teams/module-team-registry.json`, and `.codex/skills/legent-multi-thread-coordination/SKILL.md`.

Decision: multi-module operation now uses a dedicated `multi-module-coordinator` overall thread that coordinates only. It may plan, assign, monitor, rebalance, validate, review handoffs, compact memory, and update coordination metadata, but it must not implement module source code or spawn implementation subagents for module ownership areas.

Rationale: the previous hybrid coordinator could start module implementation before separate module threads joined, causing duplicate ownership and avoidable blocking. A coordination-only prompt lets the coordinator start first while later module threads join independently.

Impact: missing module threads are treated as planned/ready assignments, not blockers. Module implementation is blocked only by active overlapping source-code leases, relevant failed validation, external evidence gaps, credentials, production access, or explicit human decision. Shared `.codex` metadata leases should be short and exact.

Follow-up: use `.codex/utilities/get-module-prompt.ps1 -Module multi-module-coordinator` for the coordinator, then start module threads separately with their module prompts.

Decision update rule: add only durable decisions with source, rationale, impact, and validation or follow-up.
