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

Impact: active memory tracks current queue counts, and stale DONE/IN_PROGRESS drift is removed from the queue. Production, high-volume, provider, AI, and parity claims remain evidence-bound.

## 2026-05-24 Current Queue Source Boundary

Source: `.codex/backlog/queue.json`, `.codex/state/team-state.json`, `.codex/memory/active-work-items.md`, `.codex/dashboards/team-dashboard.md`, `.codex/threads/thread-registry.json`, and `.codex/reports/100-percent-readiness-backlog-2026-05-23.md`.

Decision: dated readiness reports are historical snapshots unless explicitly regenerated from the current queue. The current actionable source of truth is `queue.json` synchronized into team-state, dashboard, active-work memory, and the active thread registry.

Rationale: the 2026-05-23 readiness report preserved useful backlog history but its READY/BACKLOG/DONE counts became stale after the 2026-05-24 autonomous loop completed additional local work. Treating it as current caused dashboard and next-action drift.

Impact: the 2026-05-23 report now carries a historical snapshot notice, and the active coordinator points to current queue state rather than completed work. Future readiness summaries should either be regenerated from `queue.json` or clearly labeled historical.

## 2026-05-23 AI Provider Contract Boundary

Source: `AiProviderContractMeteringService.java`, `AiProviderContractRequest.java`, `AiProviderMeteringRequest.java`, `V18__ai_provider_contract_metering.sql`, `AiProviderContractMeteringServiceTest.java`, and focused foundation-service validation.

Decision: Legent may persist tenant/workspace-scoped AI provider contracts and metering events with provider disclosure, model name, data-class policy, unit/cost fields, retention/cost policy, kill switch, and hash-only evidence policy, but this remains a control plane only.

Rationale: provider-backed AI work must have policy, disclosure, metering, and fail-closed kill-switch behavior before any credentials, prompt assembly, or model calls are introduced.

Impact: metering evaluation records `providerInvoked=false`, `modelInvocation=NOT_PERFORMED`, and denied decisions for missing/incomplete disclosure, disabled metering, kill switch, provider mismatch, data-class blocks, or unit-limit breaches. No live provider call or AI parity claim is supported by this local slice.

## 2026-05-24 AI Frequency Runtime Boundary

Source: `CampaignFrequencyPolicy.java`, `CampaignEngineService.java`, `CampaignSendSafetyService.java`, `V17__campaign_frequency_optimization_decision.sql`, `CampaignEngineServiceTest.java`, `CampaignSendSafetyServiceTest.java`, and focused/cross-service Maven validation on 2026-05-24.

Decision: approved frequency optimization decisions are campaign-owned evidence snapshots. Runtime send safety may apply them only when policy key, run id, snapshot hash, recommended cap, and approval timestamp are present, and only as a reduction of an existing enabled frequency cap.

Rationale: AI optimization can influence sends only after governance evidence is persisted with the campaign. The first runtime slice must fail closed on incomplete evidence and must not let optimization raise send volume or bypass suppression, warmup, provider, or budget controls.

Impact: incomplete or unapproved optimization evidence is ignored by send safety, approved cap increases are rejected by the campaign API, and optimized cap blocks are ledgered separately as `FREQUENCY_OPTIMIZATION_CAP`. Provider/model invocation, STO scheduling, and delivery-side policy propagation remain separate evidence-gated work.

## 2026-05-23 Automation Activity Lock Boundary

Source: `AutomationActivityLockService.java`, `V9__automation_activity_lock_policy.sql`, `AutomationStudioService.java`, `AutomationStudioServiceTest.java`, `AutomationActivityLockServiceTest.java`, `frontend/src/app/(workspace)/automation/page.tsx`, and targeted backend/frontend validation.

Decision: Automation Studio live activity runs must acquire a tenant/workspace/activity lock before side effects. Concurrent active locks return a persisted `LOCKED` run response with retry-after and owner metadata. Operator override requires an explicit reason and records the override in the lock ledger and UI payload.

Rationale: live automation actions can move data, dispatch webhooks, or notify operators; concurrent execution without a visible lock policy can duplicate side effects and obscure operator accountability.

Impact: dry runs and verification remain available for safe validation, live runs are gated by lock acquisition, the UI only enables override after a lock is visible, and production release still requires target audit/RBAC evidence before claiming operational readiness.

## 2026-05-23 Segment Builder v2 Taxonomy Boundary

Source: `docs/product/salesforce-parity-matrix.md`, `docs/product/competitor-research/2026-05-20-competitor-capability-scan.md`, `frontend/src/components/audience/SegmentRuleBuilder.tsx`, official Salesforce/Klaviyo/Mailchimp/Braze segmentation sources checked on 2026-05-23, and read-only subagent findings.

Decision: Segment Builder v2 may define a docs-only taxonomy for static, membership, data-extension, behavioral, computed, consent/suppression/send-eligibility, geography/timezone, null/missing, and nested boolean rules. This taxonomy is not execution proof, UI proof, Salesforce parity, or production-scale segmentation evidence.

Rationale: current UI exposes only a small scalar/list field set, stringified values, and first-level groups, while backend and market comparison need typed operators, safety precedence, relationship metadata, event aggregation, and explain semantics before broad implementation.

Impact: execution, recompute scheduling, preview/explain, relationship indexes, governance locks, and mode-aware UI remain separate backlog items. Safety predicates must fail closed and override inclusion rules.

## 2026-05-24 Contact Relationship Contract Boundary

Source: `DataExtensionRelationship.java`, `DataExtensionRelationshipRepository.java`, `DataExtensionService.java`, `V21__data_extension_relationships.sql`, `DataExtensionServiceTest.java`, `DataExtensionQueryActivityServiceTest.java`, `DataExtensionRelationshipMigrationTest.java`, and audience-service Maven validation on 2026-05-24.

Decision: Contact Builder style data-extension relationships are now first-class tenant/workspace-scoped metadata rows with source/target data extensions, source/target fields, cardinality, required flag, active flag, ordinal, and soft-delete lifecycle. The existing relationships API remains compatible through dual persistence to `relationship_json`.

Rationale: relationship-backed segmentation and query UX must depend on declared metadata, not inferred field-name joins. Keeping traversal blocked until separate preview/explain and indexed execution slices prevents accidental high-volume or cross-workspace joins.

Impact: relationship writes validate source/target fields, same workspace, type compatibility, cardinality, and primary-key requirements before persisting rows. Query activities and Segment Builder still reject relationship paths; Contact Builder parity and production readiness remain unclaimed.

## 2026-05-24 Delivery Batch Reservation Boundary

Source: `SendRateControlService.java`, `DeliverySendReservationRepository.java`, `SendRateControlServiceTest.java`, and focused/full delivery-service Maven validation on 2026-05-24.

Decision: delivery-service may reserve a bounded batch of message reservation IDs for one tenant/workspace/provider/domain rate scope inside one transaction. The batch path uses the same warmup state, rate state, lease, idempotency, and expired-lease reclaim rules as the single-message reservation path.

Rationale: high-volume send workers otherwise take one pessimistic rate/warmup lock round trip per message. Batching same-scope reservations reduces local lock churn without weakening warmup caps, rate caps, reservation leases, or duplicate-reservation protection.

Impact: callers can use `reserveBatch` for up to 100 distinct reservation IDs in one scope. This is local correctness evidence only; provider approval, warmed sender reputation, target database lock metrics, and load proof are still required before any throughput claim.

## 2026-05-24 Segment Execution Plan Boundary

Source: `SegmentRuleExecutionPlanCompiler.java`, `SegmentEvaluationService.java`, `SegmentService.java`, `SegmentController.java`, `SegmentDto.java`, `SegmentRuleExecutionPlanCompilerTest.java`, `SegmentEvaluationServiceTest.java`, `SegmentServiceTest.java`, product docs, and focused/full audience-service Maven validation on 2026-05-24.

Decision: Segment Builder execution for existing subscriber fields, custom fields, list membership, and segment membership must compile through one bounded, parameterized execution plan before persistence, count preview cache lookup, or recompute membership deletion. Explain metadata may expose rule families, strategies, required local indexes, warnings, depth, and scoped lookup flags, but not raw values or production/parity claims.

Rationale: segment execution needs an inspectable planning boundary before broader v2 work. Compiling before cache and mutation prevents stale or unsupported persisted rules from returning cached counts or deleting memberships before failing.

Impact: count preview includes execution-plan metadata, `/api/v1/segments/{id}/execution-plan` returns read-only plan metadata, recompute compiles before deleting memberships, and relationship traversal remains fail-closed. Relationship-backed fields, behavioral event rollups, recursion governance, target performance evidence, and Segment Builder parity remain separate work.

## 2026-05-24 Send-Time Optimization Runtime Boundary

Source: `ClosedLoopOptimizationService.java`, `CampaignSendTimeOptimizationGuard.java`, `Campaign.java`, `CampaignService.java`, `OrchestrationService.java`, `SchedulingService.java`, `CampaignLaunchOrchestrationService.java`, `V18__campaign_send_time_optimization_decision.sql`, `OptimizationPerformanceServiceTest.java`, `CampaignServiceScheduleTest.java`, `OrchestrationServiceTest.java`, `SchedulingServiceTest.java`, and focused/full campaign Maven validation on 2026-05-24.

Decision: campaign scheduling may apply deterministic SEND_TIME recommendations only from a campaign-owned approved decision snapshot with policy key, run id, snapshot hash, original/recommended schedule, timezone, confidence, fallback mode, reason codes, approval evidence, rollback snapshot, and launch safety gates.

Rationale: STO must cross from foundation governance into campaign runtime as immutable evidence, not as a live model/provider call or unreviewed recommendation. The runtime path must fail closed before schedule or audience-resolution dispatch if evidence is missing, fallback-only, low-confidence, unsafe, stale, or mismatched.

Impact: Campaign schedule, Launch Command Center handoff, send-job creation, and due scheduled-job dispatch share one guard. Valid approved evidence can set the effective scheduled time; invalid evidence blocks before save or publish. Model-backed, per-recipient, journey-level, provider-capacity, production-load, and parity evidence remain separate.

## 2026-05-23 Audience Resolution Chunk Handoff Boundary

Source: `AudienceResolutionConsumer.java`, `AudienceResolutionChunkService.java`, `AudienceResolutionChunkController.java`, `CampaignEventConsumer.java`, `AudienceResolutionClient.java`, `EventContractValidator.java`, `V20__audience_resolution_chunks.sql`, and Maven validation on 2026-05-23.

Decision: `send.audience.resolved` schema v1 remains compatible with inline `subscribers`, while schema v2 is metadata-only and must reference audience-owned durable chunk storage through `chunkId`, `chunkReferenceType`, `subscriberStorage`, and `chunkUri`.

Rationale: high-volume Kafka events should not carry full recipient lists. Campaign must hydrate recipients through an audience-owned internal API rather than reading the audience database directly, then continue persisting campaign-owned `send_batch_recipients` before delivery.

Impact: audience-service persists bounded resolved chunks in `audience_resolution_chunks`, publishes schema-v2 references with chunk-aware partition keys, and exposes an internal token-protected chunk read API. Campaign-service reads referenced chunks through `AudienceResolutionClient` and keeps schema-v1 inline subscriber support for rolling compatibility.

Follow-up: local tests prove the contract path only. Production/high-volume claims still require target broker/runtime/load evidence, chunk retention policy evidence, and provider-approved throughput proof.

## 2026-05-24 AI Generation Preview Boundary

Source: `PerformanceIntelligenceController.java`, `AiGenerationPreviewService.java`, `AiGenerationPreviewRequest.java`, `AiGenerationPreviewServiceTest.java`, `PerformanceIntelligenceControllerSecurityTest.java`, and focused foundation-service Maven validation on 2026-05-24.

Decision: segment and workflow generation may be exposed only as provider-free deterministic previews under `/api/v1/performance-intelligence/ai-segments/preview` and `/api/v1/performance-intelligence/ai-workflows/preview`. Segment preview requires `audience:write`; workflow preview requires `workflow:write`. Responses must remain preview-only with `providerInvoked=false`, `modelInvocation=NOT_PERFORMED`, `applyAllowed=false`, `activationAllowed=false`, and `publishAllowed=false`.

Rationale: generation-like product surfaces are easy to mistake for model-backed or executable automation. The first local slice must use existing governance/metering audits, hash-only prompt/output evidence, bounded rule/graph shapes, and no audience/automation writes before any UI apply flow or provider integration is considered.

Impact: segment previews return safe rule-derived drafts with execution-plan metadata and fail closed on unsupported operators, relationship/data-extension traversal, reserved fields, missing values, or secret-like context. Workflow previews return graph v2 drafts using only the live runtime-supported subset. Model-backed generation, frontend apply UX, cross-service persistence, and runtime/parity evidence remain separate work.

## 2026-05-24 Journey WAIT_UNTIL Runtime Boundary

Source: `WorkflowGraphValidator.java`, `WaitUntilNodeHandler.java`, `WaitUntilNodeHandlerTest.java`, `WorkflowGraphValidatorTest.java`, `journey-node-contract.ts`, `NodeEditorModal.tsx`, `automation-builder.spec.ts`, and automation/frontend validation on 2026-05-24.

Decision: `WAIT_UNTIL` is the first advanced journey node promoted into the live runtime. It accepts only `configuration.at` or `configuration.until` as an ISO-8601 instant, schedules through the existing Quartz workflow resume path with tenant/workspace metadata, passes through when already due, and rejects waits beyond 10080 minutes.

Rationale: absolute waits can reuse the current delay/resume infrastructure without adding audience mutation, webhook dispatch, campaign send behavior, schema changes, or cross-service database access. Keeping the contract narrow avoids premature side-effect node support while allowing a concrete journey-depth expansion.

Impact: runtime capability metadata and the Journey Builder editor now expose `WAIT_UNTIL`; validation fails closed for missing, malformed, or overlong wait timestamps. Branch/split aliases, webhook/contact side-effect nodes, event-listener correlation, replay proof, and parity claims remain separate backlog work.

## 2026-05-24 Internal Service Identity Slice

Source: `InternalServiceIdentity.java`, `InternalServiceIdentityTest.java`, `AudienceResolutionClient.java`, `AudienceResolutionClientTest.java`, `AudienceResolutionChunkController.java`, `AudienceResolutionChunkControllerTest.java`, `docs/operations/production-hardening-runbook.md`, and focused/full common-audience-campaign validation on 2026-05-24.

Decision: the first local service-identity hardening slice is application-layer signed internal headers for the campaign-service audience-resolution chunk read. The request must still carry the existing internal credential, but also carries `X-Internal-Service`, `X-Internal-Signature-Timestamp`, and `X-Internal-Signature` over service name, tenant, workspace, action, job id, chunk id, and timestamp. Audience-service allows only `campaign-service` for this route.

Rationale: local Compose and Kubernetes manifests do not provide mTLS, SPIFFE, or service-mesh identity, and broad internal-route rewiring would be high blast radius. A route-specific HMAC proof binds caller identity and request scope while preserving the existing route deny and controller guard pattern.

Impact: audience chunk reads fail closed on missing signature fields, unauthorized service names, stale timestamps, wrong tenant/workspace, wrong action, or bad internal credential before service lookup. Other internal routes still need signed identity or service-JWT coverage, and production service identity remains evidence-bound.

## 2026-05-24 Enterprise Package Dry-Run Contract

Source: `EnterprisePackageService.java`, `GlobalEnterpriseController.java`, `GlobalEnterpriseDto.java`, `EnterprisePackageServiceTest.java`, `docs/product/enterprise-package-export-import-contract.md`, and focused/full foundation-service validation on 2026-05-24.

Decision: enterprise package movement starts as a Foundation-owned metadata/configuration manifest contract only. Export supports `ADMIN_SETTING`, `FEATURE_CONTROL`, `GLOBAL_OPERATING_MODEL`, `DATA_RESIDENCY_POLICY`, `ENCRYPTION_POLICY`, and `EVIDENCE_PACK`; import validation is dry-run only and never applies target changes.

Rationale: environment promotion can expose sensitive configuration or cross-workspace state if treated as generic JSON movement. The first slice must bind manifests to current tenant/workspace/environment context, checksum the manifest, hash each object, deny customer/payload object families, and block unsafe payload indicators before any live apply path exists.

Impact: `/api/v1/global/packages/export` returns a local manifest only after scope and redaction checks; `/api/v1/global/packages/import/validate` verifies scope, target environment lock state, checksum, object hashes, supported types, and returns a no-mutation diff. Live apply, rollback/compensation, approval/audit workflow, broader object-family support, production promotion evidence, and parity claims remain separate work.

## 2026-05-24 Product Parity Source Freshness Boundary

Source: `docs/product/competitor-research/2026-05-24-source-refresh.md`, `docs/product/salesforce-parity-matrix.md`, official Salesforce Help, Adobe Experience League, Braze, Klaviyo, and HubSpot sources accessed on 2026-05-24.

Decision: 2026-05-24 is the current official-source baseline for Salesforce and competitor parity research, but refreshed market sources must be treated separately from Legent implementation evidence and target runtime evidence.

Rationale: vendor product surfaces change quickly, especially AI, journey orchestration, deliverability diagnostics, and segmentation. Keeping source freshness explicit prevents stale roadmap assumptions while avoiding unsupported parity, deliverability, AI, or production-readiness claims.

Impact: the parity matrix now points to a 2026-05-24 source-refresh note and preserves local-contract wording. Remaining gaps stay evidence-bound: model-backed AI, broader executable journey/automation depth, persisted segment governance, relationship/event execution, provider capacity, target runtime proof, and production release evidence.

Decision update rule: add only durable decisions with source, rationale, impact, and validation or follow-up.
