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

## 2026-05-20 Mode-aware workflow contract

Source: `frontend/src/lib/ui-mode-contract.ts`, `frontend/src/stores/uiStore.ts`, `frontend/src/components/shell/Sidebar.tsx`, `frontend/src/app/(workspace)/campaigns/new/page.tsx`, `frontend/tests/e2e/ui-mode.spec.ts`, and `frontend/tests/e2e/campaign-engine.spec.ts`.

Status: completed local implementation cycle.

Outcome:
- Added a typed UI-mode contract for mode storage, normalization, HTML class application, feature visibility, and role-gate metadata.
- Replaced Settings navigation CSS-only hiding with render-time filtering for desktop and mobile navigation.
- Preserved Admin as session-role-gated navigation rather than a third local UI mode.
- Migrated the campaign wizard Experiment Engine to mode metadata and prevented experiment API payload submission in `BASIC` mode.

Validation:
- `cd frontend; npm run lint`
- `cd frontend; npm run build:ci`
- `cd frontend; .\node_modules\.bin\playwright.cmd test tests/e2e/ui-mode.spec.ts tests/e2e/campaign-engine.spec.ts --project=chromium --reporter=line`
- `cd frontend; npm run test:e2e:smoke`
- `.codex\utilities\validate-codex-system.ps1`
- `git diff --check`

Residual risk:
- Budget, frequency, template command-center, and automation advanced controls still need separate mode-metadata slices with payload/default-policy decisions.

## 2026-05-20 Campaign budget and frequency mode contract

Source: `frontend/src/lib/ui-mode-contract.ts`, `frontend/src/app/(workspace)/campaigns/new/page.tsx`, `frontend/tests/e2e/campaign-engine.spec.ts`, and six read-only frontend scouts.

Status: completed local implementation cycle.

Outcome:
- Added campaign mode metadata for Budget Guard and Workspace Frequency Policy.
- BASIC mode now unmounts those advanced controls and omits the hidden budget/frequency post-create API calls.
- The campaign wizard keeps the simple Frequency Cap visible in BASIC as a safety control while treating the workspace frequency policy as Advanced.

Validation:
- `cd frontend; npm run lint`
- `cd frontend; npm run build:ci`
- `cd frontend; .\node_modules\.bin\playwright.cmd test tests/e2e/campaign-engine.spec.ts tests/e2e/ui-mode.spec.ts --project=chromium --reporter=line`
- `git diff --check`

Residual risk:
- UI mode is not an authorization boundary; backend safety and authorization remain authoritative.
- Template Studio and Automation Studio still need separate mode-metadata slices.

## 2026-05-20 Automation mode contract

Source: `frontend/src/lib/ui-mode-contract.ts`, `frontend/src/app/(workspace)/automation/page.tsx`, `frontend/src/app/(workspace)/automations/builder/page.tsx`, `frontend/src/components/automation/JourneyBuilder.tsx`, `frontend/src/components/automation/NodeEditorModal.tsx`, `frontend/tests/e2e/automation-studio.spec.ts`, and `frontend/tests/e2e/automation-builder.spec.ts`.

Status: completed local implementation cycle.

Outcome:
- Added Automation Studio mode metadata for activity authoring, activity execution, manual trigger, and draft journey node types.
- BASIC mode now unmounts New Activity, Verify, Dry Run, and manual Trigger controls.
- BASIC mode blocks saving loaded draft-only journey nodes with a visible validation error instead of silently dropping unsupported nodes.
- Advanced mode keeps existing authoring, execution, and draft-node visibility behavior.

Validation:
- `cd frontend; npm run lint`
- `cd frontend; npm run build:ci`
- `cd frontend; .\node_modules\.bin\playwright.cmd test tests/e2e/automation-studio.spec.ts tests/e2e/automation-builder.spec.ts tests/e2e/ui-mode.spec.ts --project=chromium --reporter=line`
- `git diff --check`

Residual risk:
- UI mode is not authorization; backend workflow validation and activity execution safety remain authoritative.
- Template Studio still needs a separate mode-metadata slice.

## 2026-05-20 Template Studio mode contract

Source: `frontend/src/lib/ui-mode-contract.ts`, `frontend/src/app/(workspace)/email/templates/[id]/page.tsx`, `frontend/src/components/content/TemplateBuilder.tsx`, `frontend/src/components/content/TemplateStudioCommandCenter.tsx`, `frontend/tests/e2e/template-builder.spec.ts`, and six read-only frontend scouts.

Status: completed local implementation cycle.

Outcome:
- Added Template Studio mode metadata for advanced builder blocks, conditional rules, reusable content, dynamic content, personalization tokens, version operations, approval workflow, asset library, brand kits, test sends, and publish controls.
- BASIC mode keeps identity fields, core builder, Save Draft, and Preview/QA available while unmounting advanced Template Studio tabs and command/header actions.
- BASIC mode skips hidden optional resource loads for versions, approvals, assets, snippets, tokens, dynamic rules, brand kits, and test-send records.
- The builder hides advanced block types and the Rules/raw HTML surfaces in BASIC, and BASIC save payloads scrub conditional visibility settings from metadata and generated HTML row classes.

Validation:
- `cd frontend; npm run lint`
- `cd frontend; npm run build:ci`
- `cd frontend; .\node_modules\.bin\playwright.cmd test tests/e2e/template-builder.spec.ts tests/e2e/ui-mode.spec.ts --project=chromium --reporter=line`
- `.codex\utilities\validate-codex-system.ps1`
- `git diff --check`

Residual risk:
- UI mode is not authorization; content-service permissions and renderer/sanitizer behavior remain authoritative.
- BASIC mode intentionally preserves Save Draft and Preview/QA; backend validation remains the safety boundary for HTML and rendered content.

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

## 2026-05-20 AI Content Assistance Governance

Source: `services/foundation-service/src/main/java/com/legent/foundation/service/performance/AiContentAssistanceGovernanceService.java`, `services/foundation-service/src/main/resources/db/migration/V16__ai_content_assistance_governance.sql`, `services/foundation-service/src/test/java/com/legent/foundation/service/AiContentAssistanceGovernanceServiceTest.java`, and `docs/product/ai-governance-optimization-foundation.md`.

Status: completed local governance-contract implementation cycle.

Outcome:
- Added tenant/workspace-scoped AI content assistance policies and audits for draft-only assistance.
- Policy records capture feature class, provider disclosure, data-class allow/block lists, training stance, retention, opt-in/out, kill switch, draft-only mode, and required human review.
- Evaluation records deny publish, auto-publish, send, and test-send actions; draft application requires human review.
- Audit evidence stores policy version, actor, data classes, prompt template version, prompt/output hashes, guardrail findings, review decision, evidence refs, and redacted context without storing raw prompt/output or invoking a provider.

Validation:
- `.\mvnw.cmd -pl services/foundation-service -Dtest=AiContentAssistanceGovernanceServiceTest test`
- `.\mvnw.cmd -pl services/foundation-service,services/content-service -am test`

Residual risk:
- No model-provider integration or generated content application exists in this slice.
- Content-service publish/test-send review gates for AI-generated artifacts remain a separate implementation.

## 2026-05-20 Automation Studio activity orchestration split

Source: `docs/product/automation-studio-activity-orchestration-plan.md`, `AutomationStudioService.java`, `AutomationActivity.java`, `AutomationActivityRun.java`, `AudienceDataExtensionClient.java`, `WorkflowEngine.java`, and read-only backend/frontend/security/QA scouts.

Status: completed planning/decomposition cycle.

Outcome:
- Confirmed only SQL query and import activities have live execution today; other activity families remain fail-closed.
- Documented security requirements for non-secret config/result JSON, secret refs, scoped artifacts, `OutboundUrlGuard`, send handoff semantics, and script sandbox blocking.
- Added `docs/product/automation-studio-activity-security-contract.md` with the implementation order, sanitizer/redaction contract, artifact ownership model, internal route checklist, activity-family guardrails, and focused test matrix.
- Split the parent into leaseable child slices: `automation-activity-security-design`, `automation-activity-dependency-run-contract`, `automation-activity-capability-verification-ui`, `automation-file-trigger-extract-family`, `automation-webhook-notification-family`, `automation-send-activity-handoff`, and `automation-script-activity-security-sandbox`.

Residual risk:
- Dependency metadata and bounded run listing are now local, but actual multi-step execution, capability UI, file/extract, webhook, notification, send, and script sandbox work are not implemented yet.
- Kafka side effects inside workflow runtime still need outbox or after-commit hardening before expanding fan-out.

## 2026-05-20 Automation Studio dependency/run contract

Source: `AutomationStudioDto.java`, `AutomationActivity.java`, `AutomationActivityRun.java`, `AutomationActivityRunRepository.java`, `AutomationStudioService.java`, `AutomationStudioController.java`, `V7__automation_activity_dependency_run_contract.sql`, and `AutomationStudioServiceTest.java`.

Status: local implementation cycle in validation.

Outcome:
- Added tenant/workspace-scoped dependency metadata and failure policy to Automation Studio activities.
- Added dependency existence and cycle validation before create/update persistence.
- Added trace ID, error code, dependency trace JSON, and bounded pageable run listing.
- Preserved current side-effect surface: only existing SQL/import execution paths remain live-supported.
- Focused `AutomationStudioServiceTest` passed with 17 tests, including cross-workspace dependency denial, cycle rejection, dependency trace metadata, and bounded run listing.

Residual risk:
- Dependency metadata does not yet orchestrate multi-step execution; it is a validation/run-history contract for the next execution slice.
- Sanitizer/redaction utilities from the security contract are still future implementation work.

## 2026-05-20 Automation Studio capability and verification UI

Source: `frontend/src/app/(workspace)/automation/page.tsx`, `frontend/src/lib/automation-api.ts`, `frontend/tests/e2e/automation-studio.spec.ts`, and `frontend/tests/e2e/ui-mode.spec.ts`.

Status: completed local frontend implementation cycle.

Outcome:
- Automation Studio now shows per-activity capability state from supported live types and backend verification metadata.
- Unsupported executable families show draft/design/blocked state and keep dry-run side effects disabled while verification remains available.
- Activity authoring no longer uses placeholder external URLs or signed-script placeholders; per-type fields build explicit SQL/import/file/extract/webhook/script config payloads.
- Verify results, row-scoped action errors, dependency count, failure policy, run trace ID, error code, and dependency trace counts are visible in the activity surface.
- BASIC mode still hides authoring and execution controls; ADVANCED mode keeps those controls and now has payload coverage.

Validation:
- `cd frontend; npm run lint`
- `cd frontend; npm run build:ci`
- `cd frontend; .\node_modules\.bin\playwright.cmd test tests/e2e/automation-studio.spec.ts tests/e2e/ui-mode.spec.ts --project=chromium --reporter=line`

Residual risk:
- This is UI clarity only; backend validation and service safety remain authoritative.
- Live execution controls, dependency execution, sanitizer/redaction utilities, and executable file/webhook/send/script families remain separate slices.

## 2026-05-20 Flow analytics experimentation

Source: `WorkflowStudioService.java`, `InstanceHistoryRepository.java`, `TrackingDto.java`, `RawEvent.java`, `TrackingIngestionService.java`, `AnalyticsService.java`, `ClickHouseWriter.java`, `ClickHouseRollupService.java`, `V12__tracking_journey_goal_lineage.sql`, `analytics/page.tsx`, `workspace-mock.ts`, and `analytics.spec.ts`.

Status: completed local implementation cycle.

Outcome:
- Automation workflow analytics now uses a bounded recent-run window and returns step metrics, observed path signatures, journey path-test target counts, exit-goal hits, deterministic diagnostics, and evidence notes that avoid causal or AI claims.
- Tracking conversion events now have explicit journey lineage fields for workflow, version, run, step, path, goal, experiment scope, and holdout; Postgres and ClickHouse raw-event contracts include the additive columns.
- Tracking analytics exposes journey goal metrics separately from campaign experiment reporting.
- The analytics dashboard shows journey runs, step metrics, observed paths, path tests, conversion goals, deterministic signals, and tracking-owned goal conversions.

Validation:
- Focused automation, tracking, and shared Kafka tests passed.
- Full `.\mvnw.cmd -pl services/tracking-service,services/automation-service -am test` passed.
- Frontend lint, production build, and targeted Chromium analytics Playwright spec passed.
- Codex validation, repo artifact hygiene, JPA config scan, and `git diff --check` passed.

Residual risk:
- Journey path reporting is observed execution evidence only, not causal winner automation.
- ClickHouse journey-lineage DDL and high-volume behavior need target-environment proof before BI or throughput claims.
- Tracking-owned workflow-topic ingestion and rollups remain future work.

## 2026-05-20 Automation file/import/extract artifact ownership

Source: `AutomationArtifact.java`, `AutomationArtifactService.java`, `AutomationArtifactController.java`, `AutomationStudioService.java`, `V8__automation_artifact_ownership.sql`, `ImportService.java`, `automation/page.tsx`, and `automation-studio.spec.ts`.

Status: completed local implementation cycle.

Outcome:
- Added service-minted automation artifact metadata with tenant/workspace scope, source kind, status, generated object key, content type, size, SHA-256, retention, and expiry.
- Automation Studio import activities now require scoped `artifactId` references; raw object-key fields are rejected before persistence, and live import handoff records only artifact summary plus import job ID/status.
- File-drop and extract activities now support validation-only dry-run records with redacted artifact summaries while live file movement stays disabled.
- Live Automation Studio side effects now require explicit confirmation plus an idempotency key, and duplicate live import requests skip the audience handoff.
- Audience internal import start now rejects raw URLs, traversal, absolute paths, and unscoped object keys while allowing service-generated automation artifact keys.
- Automation Studio UI now sends artifact IDs for import/file/extract activity authoring and blocks unsafe file references client-side.

Validation:
- Focused automation/audience tests passed.
- Full `.\mvnw.cmd -pl services/automation-service,services/audience-service -am test` passed.
- Frontend lint, production build, and targeted Chromium Automation Studio Playwright spec passed.
- Route validation, repo artifact hygiene, Codex validation, and `git diff --check` passed.

Residual risk:
- This does not prove target object storage contents or adapter behavior.
- Live file movement, inbox polling, provider export/import, and file-transfer parity still require storage-adapter tests and target evidence.

## 2026-05-20 Automation webhook/notification activity family

Source: `AutomationStudioService.java`, `AutomationStudioDto.java`, `WebhookDispatcherService.java`, `WebhookRetryService.java`, `WebhookResponseSanitizer.java`, `automation/page.tsx`, and `automation-studio.spec.ts`.

Status: completed local implementation cycle.

Outcome:
- Automation Studio webhook activities now publish bounded `automation.*` platform events to `webhook.triggered` instead of accepting raw endpoints, methods, headers, or bodies.
- Notification activities now support live terminal-state platform notifications with explicit recipient, title, message, severity, terminal status, and app-relative link validation.
- Live webhook/notification runs require active status, explicit confirmation, and idempotency keys; duplicate live webhook runs skip repeat platform publication.
- Platform webhook delivery keeps endpoint ownership, outbound URL guard, signing, retry/idempotency behavior, and now bounds/redacts stored delivery responses and retry errors.
- Automation Studio UI exposes guarded webhook and terminal notification authoring with client-side unsafe-reference rejection and Playwright coverage.

Validation:
- Focused automation-service, platform-service, and shared common tests passed.
- Full `.\mvnw.cmd -pl services/automation-service,services/platform-service,shared/legent-common -am test` passed.
- Frontend lint, production build, and targeted Chromium Automation Studio Playwright spec passed.
- Route validation, repo artifact hygiene, Codex validation, and `git diff --check` passed.

Residual risk:
- This does not prove target platform migration application, Kafka replay behavior, production egress, or real third-party endpoint behavior.
- Governed send handoff, live file movement, and script sandboxing remain separate slices.

## 2026-05-20 Email governance policy objects

Source: `SendGovernancePolicy.java`, `SendGovernancePolicyController.java`, `SendGovernancePolicyService.java`, `V10__send_governance_policies.sql`, `Campaign.java`, `ContentServiceClient.java`, `CampaignLaunchReadinessGate.java`, `CampaignLaunchOrchestrationService.java`, `V15__campaign_send_governance_policy.sql`, `config/nginx/nginx.conf`, `infrastructure/kubernetes/ingress/ingress.yml`, and `scripts/ops/validate-route-map.ps1`.

Status: completed local implementation cycle.

Outcome:
- Content-service now owns tenant/workspace-scoped send governance policy objects with policy key, classification, sender/delivery references, sending domain, provider, unsubscribe/suppression controls, tracking, send-log retention, active state, and audit columns.
- Campaigns now store `sendGovernancePolicyId`; create/update/duplicate DTO flows preserve it, and launch controls block missing policy selection.
- Campaign preflight/direct send/Launch Command Center readiness call content-service internal policy lookup and fail closed for missing, unavailable, inactive, commercial unsafe, sender-profile mismatch, sending-domain mismatch, provider mismatch, or invalid retention policy.
- Public edge Nginx and Kubernetes ingress deny the new content internal policy endpoint, and route validation enforces the deny regex.

Validation:
- Focused content policy/RBAC/internal-token tests passed.
- Focused campaign content-client, launch-readiness, and launch-orchestration tests passed.
- Full `.\mvnw.cmd -pl services/content-service,services/campaign-service,services/delivery-service -am test` passed.
- Route validation, repo artifact hygiene, Codex validation, and `git diff --check` passed.

Residual risk:
- This is a local governance contract, not a legal compliance, Salesforce parity, inbox-placement, production-readiness, or 10 lakh throughput claim.
- Delivery-service runtime safety remains authoritative; delivery-owned profile tables and per-message immutable policy snapshots remain follow-up work.
- Target Flyway migration application, service-to-service policy lookup availability, and public-edge behavior still require environment evidence before release claims.
