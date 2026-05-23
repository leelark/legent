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

## 2026-05-22 Parity Doc Evidence Boundary

Source: `docs/product/salesforce-parity-matrix.md`, `docs/product/competitor-research/2026-05-20-competitor-capability-scan.md`, and `docs/product/automation-studio-activity-orchestration-plan.md`.

Decision: parity docs should keep completed local contracts separate from future feature work and external evidence-bound claims. Broad Segment Builder, automation, AI, Contact Builder, and delivery-policy residuals must be split into narrow backlog-ready follow-ups or blocked evidence items.

Rationale: combining completed local work with broad competitor parity language makes the backlog harder to safely select and can imply capabilities that have not been locally implemented or externally re-researched.

Impact: no new market claims were introduced in the latest doc split; future claims need a dated source refresh before product copy, roadmap commitments, or release readiness messaging.

## 2026-05-23 Parity Queue Accounting Boundary

Source: `docs/product/salesforce-parity-matrix.md`, `docs/product/competitor-research/2026-05-20-salesforce-marketing-cloud.md`, `docs/product/competitor-research/2026-05-20-competitor-capability-scan.md`, `.codex/backlog/queue.json`, and read-only scout findings from the resumed overall audit.

Decision: when a parity doc names a narrow local follow-up and the resumed audit confirms it is unblocked, the work must be represented in `.codex/backlog/queue.json`; docs-only backlog descriptions are allowed only until a queue/state lease permits safe `.codex` edits.

Rationale: an empty ready/backlog queue while docs list local follow-ups causes the autonomous loop to stop early and hides safe local progress behind external blockers.

Impact: completed local contracts are marked `DONE_LOCAL` in dated research notes, new local security/performance/frontend/docs candidates are queued for selection, and external parity, production, high-volume, provider, legal, and runtime evidence remain blocked rather than implied.

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

## 2026-05-20 Frequency Optimization Boundary

Source: `ClosedLoopOptimizationService.java`, `OptimizationPerformanceServiceTest.java`, `CampaignSendSafetyService.java`, `CampaignSendSafetyServiceTest.java`, `ReputationEngine.java`, `WarmupService.java`, `SendRateControlService.java`, and `docs/product/ai-governance-optimization-foundation.md`.

Decision: Legent may expose deterministic frequency optimization readiness, fallback, saturation, recommended-cap, confidence, approval, rollback, and safety-gate evaluation, but this is not model-backed engagement-frequency optimization and must not control live cadence automatically.

Rationale: current local evidence supports rules-based frequency policy evaluation and tenant/workspace frequency-cap enforcement before delivery handoff. Current evidence does not prove model/provider integration, weekly model refresh, live journey/campaign cadence control, provider-approved capacity, or production send evidence.

Impact: commercial frequency optimization must exclude transactional/test-send history unless a separate policy exists. Cap increases require human approval, rollback evidence, and pass/fail evidence for suppression, unsubscribe/preference, warmup, rate-limit, provider capacity, deliverability, and frequency-ledger gates. Delivery and deliverability controls can only reduce or defer sends after approval; frequency recommendations cannot override them. Scheduled sends must revalidate readiness at execution time or carry an immutable approved frequency snapshot.

Follow-up: implement first-class AI policy/audit persistence, live cadence/journey integration, scheduled-send revalidation or immutable snapshot tests, provider-capacity proof, direct reputation/warmup regression tests, and target evidence before any model-backed frequency or parity claim.

## 2026-05-20 AI Content Assistance Governance Boundary

Source: `AiContentAssistanceGovernanceService.java`, `AiContentAssistancePolicyRequest.java`, `AiContentAssistanceEvaluateRequest.java`, `V16__ai_content_assistance_governance.sql`, `AiContentAssistanceGovernanceServiceTest.java`, `docs/product/ai-governance-optimization-foundation.md`, and `docs/product/salesforce-parity-matrix.md`.

Decision: Legent may persist tenant/workspace-scoped draft-only AI content assistance policy and audit records, but this is a governance control plane only. It does not call model providers, generate content, auto-apply output, publish, send, or prove Salesforce Einstein parity.

Rationale: current local source still has no model-provider integration. The safe first slice is to define policy and audit invariants before any provider credentials, prompt assembly, model calls, content workflow application, or public product claim.

Impact: AI content policies require provider disclosure, allowed/prohibited data classes, training stance, retention policy, opt-in/out, kill switch, draft-only mode, and human review. Evaluation stores prompt/output hashes only, redacts secret-like context keys, records policy version/actor/data classes/review decision, never invokes a provider, and denies publish, auto-publish, send, or test-send actions.

Follow-up: implement model-provider security review, content-service draft application hooks, publish/test-send review gates for AI-generated artifacts, usage metering, and target evidence before generated-content or AI parity claims.

## 2026-05-20 Multi-Module Coordinator Boundary

Source: `.codex/prompts/multi-module-coordinator-24x7.md`, `.codex/workflows/multi-thread-autonomous-teams.md`, `.codex/teams/module-team-registry.json`, and `.codex/skills/legent-multi-thread-coordination/SKILL.md`.

Decision: multi-module operation now uses a dedicated `multi-module-coordinator` overall thread that coordinates only. It may plan, assign, monitor, rebalance, validate, review handoffs, compact memory, and update coordination metadata, but it must not implement module source code or spawn implementation subagents for module ownership areas.

Rationale: the previous hybrid coordinator could start module implementation before separate module threads joined, causing duplicate ownership and avoidable blocking. A coordination-only prompt lets the coordinator start first while later module threads join independently.

Impact: missing module threads are treated as planned/ready assignments, not blockers. Module implementation is blocked only by active overlapping source-code leases, relevant failed validation, external evidence gaps, credentials, production access, or explicit human decision. Shared `.codex` metadata leases should be short and exact.

Follow-up: use `.codex/utilities/get-module-prompt.ps1 -Module multi-module-coordinator` for the coordinator, then start module threads separately with their module prompts.

## 2026-05-23 Deployment Manager Evidence Boundary

Source: `frontend/src/components/settings/EnterpriseSettingsConsole.tsx`, `docs/operations/deployment-manager-provider-options.md`, official provider documentation checked on 2026-05-23, and validation from `npm run lint`, `npm run build:ci`, targeted admin Playwright, repo artifact hygiene, release evidence validator self-tests, and Codex system validation.

Decision: the Deployment Manager settings surface may present `LOCAL_VALIDATION` and `PRODUCTION_MANAGED` tracks, runtime config keys, and 100% local readiness targets, but it must keep production, Salesforce-class parity, AI parity, and 10 lakh / 10h send claims evidence-bound.

Rationale: local code, docs, and validators can complete the planning/configuration path; they cannot create paid provider quota, warmed sender reputation, target runtime smoke, image provenance, restore, monitoring, or live load evidence.

Impact: free/local path is validation-only. Paid production path requires dated provider/runtime/registry/monitoring evidence before strict release or throughput claims.

Decision update rule: add only durable decisions with source, rationale, impact, and validation or follow-up.

## 2026-05-23 100 Percent Readiness Backlog Boundary

Source: .codex/backlog/queue.json, .codex/reports/100-percent-readiness-backlog-2026-05-23.md, six read-only scout outputs, and local validation/memory audit from 2026-05-23.

Decision: queue.json is the source of truth for actionable 100 percent readiness work. Current-state memory files should contain live risks, blockers, summaries, and next actions only; completed work details belong in queue doneWork, checkpoints, audit events, handoffs, and reports.

Rationale: verbose completed narratives made active memory stale and hid local READY/BACKLOG work behind external blockers. Compact memory keeps the autonomous loop selectable while preserving detailed history in durable artifacts.

Impact: active memory now lists 15 READY, 20 BACKLOG, and 16 BLOCKED queue items, and stale DONE/IN_PROGRESS drift is removed from the queue. Production, high-volume, provider, AI, and parity claims remain evidence-bound.
