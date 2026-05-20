# Enhancement Log

Fresh baseline date: 2026-05-20.

## 2026-05-20 Salesforce and competitor parity refresh

Source: `docs/product/salesforce-parity-matrix.md`, `docs/product/competitor-research/2026-05-20-salesforce-marketing-cloud.md`, `docs/product/competitor-research/2026-05-20-competitor-capability-scan.md`, current source audit of `services/**` and `frontend/src/**`.

Status: completed research cycle.

Key themes:
- Email/content capabilities are partially implemented, but explicit send governance objects are not first-class yet.
- Audience/data-extension capabilities are partially implemented, but Contact Builder style relationship/cardinality governance and deletion/provenance audit need decomposition.
- Journey and automation surfaces are partially implemented; the immediate READY risk is that the visual Journey Builder exposes node types beyond proven runtime support.
- Deliverability controls exist locally, but external provider capacity, reputation, egress, and live load evidence remain blocked.
- Analytics exists for core campaign/event metrics and rollups, but journey step analytics, attribution depth, and anomaly evidence are partial.
- Current local intelligence features are deterministic or heuristic; true model-backed AI parity is not proven.
- `BASIC` and `ADVANCED` UI modes exist, but role-gated Admin mode is not yet a current product contract.

Queue items added:
- READY: `journey-runtime-node-contract`.
- BACKLOG: `ai-governance-optimization-foundation`, `email-governance-policy-objects`, `contact-data-designer-governance`, `automation-studio-activity-orchestration`, `mode-aware-workflow-contract`, `flow-analytics-experimentation`.

## 2026-05-20 Journey runtime node contract

Source: `services/automation-service/src/main/java/com/legent/automation/service/WorkflowGraphValidator.java`, `WorkflowStudioService.java`, `WorkflowEngine.java`, `frontend/src/components/automation/JourneyBuilder.tsx`, `NodeEditorModal.tsx`, and `frontend/tests/e2e/automation-builder.spec.ts`.

Status: completed implementation cycle.

Outcome:
- Live journey runtime support is explicit: `ENTRY_TRIGGER`, `SEND_EMAIL`, `DELAY`, `CONDITION`, and `END`.
- Known but unsupported journey node types remain loadable as draft-only design elements and cannot be newly selected or published until runtime support is added.
- Publish/activate requires affirmative backend validation, and backend runtime/start/resume/rollback paths fail closed for unsupported graph semantics.
- Simulation/dry-run reports unsupported runtime nodes instead of implying generic execution.

Validation:
- `.\mvnw.cmd -pl services/automation-service -am test`
- `cd frontend; npm run lint`
- `cd frontend; npm run build:ci`
- `cd frontend; npx playwright test tests/e2e/automation-builder.spec.ts --project=chromium`
- `.codex\utilities\validate-codex-system.ps1`
- `git diff --check`

Current enhancement themes to discover and score:
- Salesforce-parity gaps across email, journeys, automations, data, analytics, deliverability, governance, and AI assistance.
- Beginner, advanced, and admin workflow completeness, with Admin treated as role-gated until implemented.
- High-volume campaign send safety and throughput.
- Observability, release evidence, and operational readiness.
- UI/UX density, navigation, and workflow efficiency.

No enhancement is considered selected until it is scored, scoped, assigned, checkpointed, and validated.

## 2026-05-20 AI governance and optimization foundation

Source: `docs/product/ai-governance-optimization-foundation.md`, `docs/product/salesforce-parity-matrix.md`, `PerformanceIntelligenceController.java`, `OperationsAssistanceService.java`, `ClosedLoopOptimizationService.java`, and current official vendor AI trust/optimization sources.

Status: completed design cycle; enforcement remains future implementation work.

Outcome:
- Defined Legent's AI claim taxonomy: deterministic heuristic, predictive model, generative model, decisioning, autonomous action, and evidence-required.
- Documented tenant/workspace data-use policy requirements for provider disclosure, allowed data classes, training stance, retention, masking/minimization, opt-in/out, kill switch, metering, and audit.
- Established human-review, confidence/fallback, minimum data threshold, and deliverability-safety requirements for content assistance, send-time optimization, predictive segments, and frequency optimization.
- Split model-backed AI work into four follow-up backlog slices: `ai-content-assistance-governance`, `send-time-optimization-governance`, `predictive-segments-governance`, and `frequency-optimization-governance`.

Residual risk:
- Current source still has no model-provider implementation or enforcement policy. Do not claim true AI parity or integrate providers until follow-up slices add implementation and tests.

## 2026-05-20 Send-time optimization governance

Source: `services/foundation-service/src/main/java/com/legent/foundation/service/performance/ClosedLoopOptimizationService.java`, `services/foundation-service/src/test/java/com/legent/foundation/service/OptimizationPerformanceServiceTest.java`, `docs/product/ai-governance-optimization-foundation.md`, and `docs/product/salesforce-parity-matrix.md`.

Status: completed local implementation cycle.

Outcome:
- `SEND_TIME` is now an explicit deterministic optimization policy type in foundation performance intelligence.
- STO evaluation now returns confidence band, fallback mode, data-quality reasons, and lookback evidence.
- Low-data signals fall back to a default schedule recommendation instead of implying personalized timing confidence.
- Commercial and transactional engagement data are separated by policy, launch-time changes require approval and rollback evidence, and quiet-hours, approval, suppression, warmup, rate-limit, provider-capacity, and deliverability gates must pass before launch timing can be changed.
- Docs now state that this is governance/readiness evidence only, not model-backed STO, not weekly model recreation, and not live predictive scheduling.

Validation:
- `.\mvnw.cmd -pl services/foundation-service -am "-Dtest=OptimizationPerformanceServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- `.\mvnw.cmd -pl services/foundation-service -am test`

Residual risk:
- Campaign/delivery runtime scheduling integration, model-backed STO, target-environment data quality, and live scheduling evidence remain future work before Salesforce Einstein STO parity or production claims.

## 2026-05-20 Automation Studio run-history visibility

Source: `frontend/src/app/(workspace)/automation/page.tsx`, `frontend/tests/e2e/automation-studio.spec.ts`, and existing `listAutomationActivityRuns` API client helper.

Status: completed local implementation cycle.

Outcome:
- Automation Studio activities now have per-activity expandable recent run history using the existing run-history API.
- The UI shows run status, dry/live mode, trigger source, rows read/written, started/completed timestamps, failure message when present, and scoped loading, empty, and error states.
- Expanded run history refreshes after a dry run completes for that activity.
- No live-run button, new activity execution type, external credential surface, or broader orchestration parity claim was added.

Validation:
- `cd frontend; npm run lint`
- `cd frontend; npm run build:ci`
- `cd frontend; .\node_modules\.bin\playwright.cmd test automation-studio.spec.ts --project=chromium --reporter=line`

Residual risk:
- Backend run-history remains unpaged in this slice; richer run trace, dependency ordering, notifications, failure policy, and activity-family execution parity remain under the broader `automation-studio-activity-orchestration` backlog item.

## 2026-05-20 ClickHouse experiment lineage and rollup idempotency

Source: `services/tracking-service/src/main/java/com/legent/tracking/service/ClickHouseWriter.java`, `services/tracking-service/src/main/java/com/legent/tracking/service/ClickHouseRollupService.java`, `services/tracking-service/src/test/java/com/legent/tracking/service/ClickHouseWriterTest.java`, and `services/tracking-service/src/test/java/com/legent/tracking/service/ClickHouseRollupServiceTest.java`.

Status: completed local implementation cycle.

Outcome:
- Raw ClickHouse event ingestion now writes campaign experiment lineage fields: `experiment_id`, `variant_id`, and `holdout`.
- The raw event schema metadata and ordering include the lineage fields so downstream BI can distinguish experiment cohorts.
- Campaign-day rollup refresh now deletes the scoped tenant/workspace/date window before reinsert, making repeated refreshes locally idempotent for that window.

Validation:
- `.\mvnw.cmd -pl services/tracking-service -am '-Dtest=ClickHouseWriterTest,ClickHouseRollupServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `.\mvnw.cmd -pl services/tracking-service -am test`
- `.codex\utilities\validate-codex-system.ps1`
- `scripts\ops\validate-repo-artifact-hygiene.ps1`
- `git diff --check`

Residual risk:
- Target ClickHouse DDL migration/application and high-volume refresh behavior still require environment evidence before release, throughput, or BI-grade experiment claims.
