# Successful Fixes

Fresh baseline date: 2026-05-20.

## 2026-05-22 Route Map Ingress Prefix Coverage

Source: `scripts/ops/validate-route-map.ps1`, `config/gateway/route-map.json`, and `infrastructure/kubernetes/ingress/ingress.yml`.

Outcome: route validation now parses Kubernetes ingress path/backend pairs, evaluates regex path coverage in file order, and fails when a route-map prefix has no Kubernetes ingress coverage or first-matches a different backend service. This protects specific-before-broad ownership such as platform admin aliases and public landing pages.

Validation: route-map validation passed for 49 routes and 8 source-discovered internal routes; a temporary ingress fixture with `/api/v1/templates` removed from the content ingress regex failed as expected; production Kustomize render, production overlay validation, repository artifact hygiene, Codex validation, and scoped `git diff --check` passed.

Residual risk: this is local route ownership validation only. Target ingress-controller behavior and strict production release evidence remain external requirements.

## 2026-05-22 Workspace Context Fail-Closed E2E

Source: `frontend/tests/e2e/context-bootstrap.spec.ts`, with existing behavior in `frontend/src/app/(workspace)/layout.tsx` and `frontend/src/lib/context-bootstrap.ts`.

Outcome: the workspace bootstrap Playwright spec now includes a deterministic mocked session where identity succeeds but no workspace context is available. The test proves the app clears locally cached user, role, tenant, workspace, and environment context and redirects to login rather than rendering workspace navigation.

Validation: frontend lint passed; frontend production build passed; targeted Chromium `context-bootstrap.spec.ts` passed with 1 mocked fail-closed case and 1 expected credential-gated smoke skip; repository artifact hygiene, Codex validation, and scoped `git diff --check` passed.

Residual risk: this is local browser validation only. Target auth/session behavior and edge deployment evidence remain part of normal release gates.

## 2026-05-22 Campaign Scheduled Publish Workspace Context

Source: `services/campaign-service/src/main/java/com/legent/campaign/service/SchedulingService.java`, `services/campaign-service/src/main/java/com/legent/campaign/event/CampaignEventPublisher.java`, `services/campaign-service/src/test/java/com/legent/campaign/service/SchedulingServiceTest.java`, and `services/campaign-service/src/test/java/com/legent/campaign/event/CampaignEventPublisherTest.java`.

Outcome: scheduled campaign job processing now requires tenant/workspace ownership from the due job row, claims the job under that scope, and installs tenant/workspace context around the audience-resolution publish call. The publisher-generated envelope and payload now carry workspace ownership for scheduled sends, and previous thread context is restored afterward.

Validation: focused scheduler/publisher tests passed with 7 tests; full `.\mvnw.cmd -pl services/campaign-service -am test` passed with 107 campaign-service tests plus upstream shared modules; repository artifact hygiene, Codex validation, and scoped `git diff --check` passed.

Residual risk: this is local scheduler/Kafka contract evidence only. Target multi-replica scheduler behavior, provider capacity, queue-lag, and release evidence remain separate requirements before production throughput claims.

## 2026-05-22 Release Gate Strict Skip Guard

Source: `scripts/ops/release-gate.ps1` and `scripts/ops/test-release-evidence-validators.ps1`.

Outcome: strict non-local release-gate mode now fails immediately when invoked with local gate skip flags, preserving full backend, frontend, Compose, and Kustomize validation expectations for promotion-style runs. `-LocalOnly` mode still supports skip flags for non-promotional local validation.

Validation: release evidence validator self-test passed with a new strict-skip negative fixture; local-only release gate with all skip flags passed; repository artifact hygiene passed; scoped `git diff --check` passed with CRLF warnings only.

Residual risk: this is local validator hardening only. Strict release remains blocked until real target evidence exists and all unskipped promotion gates pass.

## 2026-05-21 Deliverability Controller RBAC Coverage

Source: `services/deliverability-service/src/main/java/com/legent/deliverability/controller/SuppressionController.java`, `DmarcController.java`, `ReputationController.java`, and `services/deliverability-service/src/test/java/com/legent/deliverability/controller/DomainControllerRbacTest.java`.

Outcome: suppression list/history, DMARC report reads, and reputation score reads now require `deliverability:read`; DMARC ingest now requires `deliverability:write`. Internal suppression service endpoints keep their existing internal credential guard instead of principal-based RBAC.

Validation: focused deliverability controller tests passed with 17 tests; full `.\mvnw.cmd -pl services/deliverability-service -am test` passed with 50 deliverability-service tests plus upstream shared-module tests; artifact hygiene, Codex validation, and scoped `git diff --check` passed.

Residual risk: this is local method-security evidence only. Target auth filter-chain behavior and release promotion still require the normal release evidence gates.

## 2026-05-21 Campaign Scheduler Bounded Claims

Source: `services/campaign-service/src/main/java/com/legent/campaign/service/SchedulingService.java`, `SendExecutionService.java`, `SendJobRepository.java`, `SendBatchRepository.java`, and focused campaign scheduler/send-execution tests.

Outcome: scheduled send-job processing now reads a bounded page of due jobs and atomically claims a tenant/workspace job from `PENDING` to `RESOLVING` before publishing audience-resolution work. Partial batch retry processing now bounds stale `PROCESSING` recovery and `PARTIAL` retry scans, and uses compare-and-claim updates before requeueing or terminally failing a batch.

Validation: focused scheduler/send-execution tests passed with 24 tests; full `.\mvnw.cmd -pl services/campaign-service -am test` passed with 95 tests; Codex system validation passed; scoped `git diff --check` passed with CRLF warnings only.

Residual risk: this is local scheduler correctness evidence only. Target multi-replica scheduler behavior, Kafka lag, DB lock behavior, and high-volume send proof remain release/performance evidence requirements.

## 2026-05-21 Content Send Governance Internal Security Chain

Source: `services/content-service/src/main/java/com/legent/content/config/SecurityConfig.java`, `services/content-service/src/test/java/com/legent/content/config/SecurityConfigTest.java`, and `services/content-service/src/test/java/com/legent/content/controller/SendGovernancePolicyControllerTest.java`.

Outcome: content-service now permits `GET /api/v1/content/send-governance-policies/{id}/internal` through the service security filter chain so campaign-service can reach the existing controller-level internal-token guard. Invalid internal tokens still fail closed before service access.

Validation: focused content tests passed with 12 tests; route-map validation passed; Codex system validation passed; scoped `git diff --check` passed with CRLF warnings only.

Residual risk: this is local security-chain evidence only. Public Nginx/ingress denies still need to remain in route validation, and target service-to-service availability/edge behavior remains release evidence rather than a production claim.

## 2026-05-21 Tracking Ingestion Batch Consumer Local Hardening

Source: `TrackingEventConsumer.java`, `TrackingKafkaConsumerConfig.java`, `TrackingEventIdempotencyService.java`, `TrackingEventFinalizationService.java`, `ClickHouseRollupService.java`, `V13__tracking_idempotency_raw_write_phase.sql`, focused tracking tests, and shared Kafka tests.

Outcome: tracking ingestion now consumes `tracking.ingested` as Kafka batches with fail-closed envelope/scope checks, same-batch duplicate filtering, raw-write/finalization idempotency phases, and a tracking-only long-horizon retry handler so stale `IN_PROGRESS` claims can be reclaimed before DLQ recovery. Docker-independent migration coverage now runs without Testcontainers, and local/service ClickHouse schema setup repairs writer lineage columns.

Validation: focused tracking validation passed with 48 tests and 7 Docker-gated skips; focused shared Kafka validation passed with 32 tests; full `.\mvnw.cmd -pl shared/legent-kafka,services/tracking-service -am test` passed with tracking-service 70 tests and 7 Docker-gated skips; Codex system, thread coordination, lease, artifact hygiene, and scoped diff checks passed.

Residual risk: the item is blocked, not done. PostgreSQL/Flyway idempotency behavior still needs Docker or target evidence, and ClickHouse raw-events dedupe/reconciliation for ambiguous or partial batch writes remains unproven.

## 2026-05-20 Foundation Config Version History Scope

Source: `services/foundation-service/src/main/java/com/legent/foundation/domain/ConfigVersionHistory.java`, `ConfigVersionHistoryRepository.java`, `ConfigVersioningService.java`, `ConfigService.java`, `ConfigVersionController.java`, `AdminSettingsService.java`, `services/foundation-service/src/main/resources/db/migration/V17__config_version_history_scope.sql`, and focused foundation tests.

Outcome: config version history now records workspace/environment scope, exact nullable scope predicates are used for history, compare, rollback, and version numbering, rollback uses `ConfigRepository.findByScope`, rollback config side effects invalidate/publish through `ConfigService`, history writes fail the mutation if version recording fails, and V17 adds an exact-scope unique key/version index.

Validation: focused `ConfigVersioningServiceTest,ConfigServiceTest,AdminSettingsServiceTest,ConfigVersionControllerTest` passed with 38 tests; full `.\mvnw.cmd -pl services/foundation-service -am test` passed with foundation-service and upstream shared modules; Codex validation, monitor check, repo artifact hygiene, and scoped `git diff --check` passed.

Residual risk: legacy pre-V17 history rows remain nullable because historical workspace/environment ownership cannot be safely inferred; target databases should be checked for duplicate tenant/key/version rows before migration.

## 2026-05-20 Automation Send Handoff Review Hardening

Source: `services/automation-service/src/main/java/com/legent/automation/service/node/SendEmailNodeHandler.java`, `services/automation-service/src/main/java/com/legent/automation/service/AutomationStudioService.java`, `services/automation-service/src/main/java/com/legent/automation/service/WorkflowGraphValidator.java`, `services/campaign-service/src/main/java/com/legent/campaign/event/CampaignEventConsumer.java`, `shared/legent-kafka/src/main/java/com/legent/kafka/producer/EventContractValidator.java`, and focused automation/campaign/Kafka tests.

Outcome: review findings on the governed send handoff are fixed locally. Workflow `SEND_EMAIL` now claims before Kafka publish, marks processed only after successful publish, and releases the pending claim on publish failure. Send override keys are normalized so snake/kebab/case variants cannot bypass governed-campaign handoff rules. `send.requested` producer validation now rejects non-true `confirmLaunch` and envelope/payload idempotency mismatches before Kafka. Campaign consumption now fails closed when a `subscriberId` trace appears without explicit `CAMPAIGN_ORCHESTRATION` handoff markers, while tests document campaign-wide audience semantics.

Validation: focused `SendEmailNodeHandlerTest,AutomationStudioServiceTest,WorkflowGraphValidatorTest,CampaignEventConsumerTest,OrchestrationServiceTest,EventContractValidatorTest` passed with 89 tests; full `.\mvnw.cmd -pl services/automation-service,services/campaign-service -am test` passed across shared dependencies, campaign, and automation.

Residual risk: this is local backend contract evidence only. Production send proof, target Kafka replay, target Flyway/migration proof, provider capacity, deliverability evidence, and high-volume load evidence remain separate release blockers.

## 2026-05-20 Automation Send Activity Handoff

Source: `services/automation-service/src/main/java/com/legent/automation/service/AutomationStudioService.java`, `services/automation-service/src/main/java/com/legent/automation/service/node/SendEmailNodeHandler.java`, `services/automation-service/src/main/java/com/legent/automation/event/WorkflowEventPublisher.java`, `services/automation-service/src/main/java/com/legent/automation/service/WorkflowGraphValidator.java`, `services/campaign-service/src/main/java/com/legent/campaign/event/CampaignEventConsumer.java`, `services/campaign-service/src/main/java/com/legent/campaign/event/CampaignEventPublisher.java`, `services/campaign-service/src/main/java/com/legent/campaign/service/OrchestrationService.java`, `shared/legent-kafka/src/main/java/com/legent/kafka/producer/EventContractValidator.java`, and focused automation/campaign/Kafka tests.

Outcome: Automation Studio `SEND_EMAIL` activities and workflow `SEND_EMAIL` nodes now hand off only confirmed `send.requested` campaign launch commands with tenant/workspace context, source activity/run or workflow/node identity, deterministic idempotency, and campaign-service send lifecycle ownership. Unsafe recipient, content, sender, provider, governance-policy, and safety-control overrides are rejected; campaign consumption now fails closed on missing confirmation, workspace, or idempotency.

Validation: focused automation tests passed with 40 tests, focused campaign tests passed with 29 tests, shared Kafka contract tests passed with 16 tests, and full `.\mvnw.cmd -pl services/automation-service,services/campaign-service,services/delivery-service,services/deliverability-service -am test` passed.

Residual risk: this is a local governed handoff contract, not production send evidence, Salesforce parity, inbox-placement proof, or 10 lakh throughput proof. Delivery-owned immutable policy snapshots, target Kafka replay, provider capacity, deliverability, production egress, and live send-path evidence remain required before release claims.

## 2026-05-20 Suppression Delete Tenant Scope

Source: `services/audience-service/src/main/java/com/legent/audience/service/SuppressionService.java`, `services/audience-service/src/main/java/com/legent/audience/repository/SuppressionRepository.java`, `services/audience-service/src/test/java/com/legent/audience/service/SuppressionServiceTest.java`.

Outcome: audience suppression delete now fails closed outside the current tenant/workspace and no longer performs raw ID deletion.

Validation: `.\mvnw.cmd -pl services/audience-service -am '-Dtest=SuppressionServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` passed with 2 tests.

## 2026-05-20 Segment Rules Fail Closed

Source: `services/audience-service/src/main/java/com/legent/audience/service/SegmentEvaluationService.java`, `services/audience-service/src/main/java/com/legent/audience/service/SegmentService.java`, `services/audience-service/src/test/java/com/legent/audience/service/SegmentEvaluationServiceTest.java`, `services/audience-service/src/test/java/com/legent/audience/service/SegmentServiceTest.java`.

Outcome: audience segment evaluation now honors `list_membership` `IN_LIST` and `NOT_IN_LIST` rules with tenant/workspace-scoped membership SQL, and invalid operators or list/operator combinations fail closed before evaluation or persistence.

Validation: focused segment tests passed with 13 tests, full `.\mvnw.cmd -pl services/audience-service -am test` passed with 89 tests, Codex system validation passed, repository artifact hygiene passed, and `git diff --check` passed with only CRLF warnings.

## 2026-05-20 Automation Studio Live Run Confirmation

Source: `services/automation-service/src/main/java/com/legent/automation/dto/AutomationStudioDto.java`, `services/automation-service/src/main/java/com/legent/automation/service/AutomationStudioService.java`, `services/automation-service/src/test/java/com/legent/automation/service/AutomationStudioServiceTest.java`.

Outcome: Automation Studio run requests now default omitted `dryRun` to dry-run, and live execution requires explicit `confirmLiveRun=true` in addition to existing ACTIVE, verification, and live-supported type gates.

Validation: focused automation tests passed with 16 tests, full `.\mvnw.cmd -pl services/automation-service -am test` passed with 47 tests, Codex system validation passed, repository artifact hygiene passed, and `git diff --check` passed with only CRLF warnings.

## 2026-05-20 Platform Event Idempotency

Source: `services/platform-service/src/main/java/com/legent/platform/event/PlatformEventConsumer.java`, `services/platform-service/src/main/java/com/legent/platform/service/PlatformEventIdempotencyService.java`, `services/platform-service/src/main/resources/db/migration/V7__platform_event_idempotency.sql`, `services/platform-service/src/test/java/com/legent/platform/event/PlatformEventConsumerTest.java`, `services/platform-service/src/test/java/com/legent/platform/service/PlatformEventIdempotencyServiceTest.java`.

Outcome: platform webhook, notification, and search consumers now claim event/idempotency identity before side effects, skip duplicate replay, release pending claims on pre-completion side-effect failures, and avoid releasing claims after side effects complete if processed-marker updates fail.

Validation: focused platform idempotency tests passed with 20 tests, full `.\mvnw.cmd -pl services/platform-service -am test` passed with 71 tests, Codex system validation passed, repository artifact hygiene passed, and `git diff --check` passed with only CRLF warnings.

## 2026-05-20 Audience Resolution Final Eligibility First Slice

Source: `services/audience-service/src/main/java/com/legent/audience/event/AudienceResolutionConsumer.java`, `services/audience-service/src/main/java/com/legent/audience/service/SendEligibilityService.java`, `services/audience-service/src/main/java/com/legent/audience/repository/SuppressionRepository.java`, `services/audience-service/src/test/java/com/legent/audience/event/AudienceResolutionConsumerTest.java`, `services/audience-service/src/test/java/com/legent/audience/service/SendEligibilityServiceTest.java`.

Outcome: audience resolution now runs resolved subscribers through authoritative batch eligibility before campaign batching, including local audience suppressions, nested email-channel opt-out, and future pause windows. Eligibility failures abort publish instead of producing partially unchecked chunks.

Validation: focused audience eligibility tests passed with 23 tests, full `.\mvnw.cmd -pl services/audience-service -am test` passed with 96 tests, Codex system validation passed, repository artifact hygiene passed, and `git diff --check` passed with only CRLF warnings.

## 2026-05-20 ClickHouse Experiment Lineage And Rollup Idempotency

Source: `services/tracking-service/src/main/java/com/legent/tracking/service/ClickHouseWriter.java`, `services/tracking-service/src/main/java/com/legent/tracking/service/ClickHouseRollupService.java`, `services/tracking-service/src/test/java/com/legent/tracking/service/ClickHouseWriterTest.java`, `services/tracking-service/src/test/java/com/legent/tracking/service/ClickHouseRollupServiceTest.java`.

Outcome: ClickHouse raw event ingestion now preserves `experiment_id`, `variant_id`, and `holdout`, and campaign-day rollup refresh deletes the scoped tenant/workspace/date range before reinsert so repeated refreshes do not double count.

Validation: focused ClickHouse writer/rollup tests passed with 5 tests, full `.\mvnw.cmd -pl services/tracking-service -am test` passed with 41 tests, Codex system validation passed, repository artifact hygiene passed, and `git diff --check` passed with only CRLF warnings.

## 2026-05-20 Codex Lifecycle State Atomicity

Source: `.codex/utilities/codex-state.ps1`, `acquire-lease.ps1`, `start-work-item.ps1`, `complete-work-item.ps1`, `promote-backlog-item.ps1`, `new-checkpoint.ps1`, `update-checkpoint.ps1`, `write-audit-event.ps1`, `register-thread.ps1`, `heartbeat-thread.ps1`, and `test-codex-lifecycle-state.ps1`.

Outcome: core autonomous lifecycle mutations now use a shared named state lock and atomic JSON writes. Lease acquisition rejects overlapping active scopes before persisting, `start-work-item` creates lease/checkpoint evidence before queue transition, completion releases active leases and removes released lease IDs from the thread record, audit append is locked, and a disposable temp-copy smoke covers promote/start/update/overlap-reject/complete/audit behavior.

Validation: `test-codex-lifecycle-state.ps1` passed, `.codex\utilities\validate-codex-system.ps1` passed, `.codex\utilities\validate-worktree-leases.ps1` passed, PowerShell parser checks passed for `.codex\utilities`, and `git diff --check` passed with only CRLF warnings.

## 2026-05-20 Segment Rule Builder Operator Filtering

Source: `frontend/src/components/audience/SegmentRuleBuilder.tsx`, `frontend/tests/e2e/audience-segments.spec.ts`.

Outcome: the segment builder now shows scalar operators only for scalar fields and `IN_LIST`/`NOT_IN_LIST` only for `list_membership`. Loaded or edited invalid field/operator combinations are normalized before submit, and the focused Playwright spec verifies the UI options and posted payload.

Validation: frontend lint passed, frontend production build passed, `.\node_modules\.bin\playwright.cmd test tests/e2e/audience-segments.spec.ts --project=chromium --reporter=line` passed with 1 Chromium test, Codex system validation passed, repository artifact hygiene passed, and `git diff --check` passed with only CRLF warnings. `npx playwright ...` hung in this environment, so the local Playwright binary was used for the targeted E2E evidence.

## 2026-05-20 Send-Time Optimization Governance

Source: `services/foundation-service/src/main/java/com/legent/foundation/service/performance/ClosedLoopOptimizationService.java`, `services/foundation-service/src/test/java/com/legent/foundation/service/OptimizationPerformanceServiceTest.java`, `docs/product/ai-governance-optimization-foundation.md`, and `docs/product/salesforce-parity-matrix.md`.

Outcome: foundation performance intelligence now accepts `SEND_TIME` optimization policies and evaluates deterministic STO readiness with confidence band, fallback mode, data-quality reasons, commercial/transactional data separation, human approval and rollback requirements for launch-time changes, and fail-closed safety gates for quiet hours, approvals, suppressions, warmup, rate limits, provider capacity, and deliverability.

Validation: focused optimization tests passed with 7 tests, and full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 91 foundation/reactor tests.

Residual risk: this is not model-backed STO or live runtime scheduling. Campaign/delivery integration, timezone behavior, model/data provenance, and target-environment evidence remain future work before STO parity or production claims.

## 2026-05-20 Automation Studio Run-History Visibility

Source: `frontend/src/app/(workspace)/automation/page.tsx`, `frontend/tests/e2e/automation-studio.spec.ts`.

Outcome: Automation Studio activities now expose a compact per-activity recent run-history panel with status, dry/live mode, trigger source, row counts, timestamps, error messages, scoped loading/empty/error states, manual refresh, and automatic refresh after a dry run completes.

Validation: frontend lint passed, frontend production build passed, and the targeted Chromium Playwright spec passed with 2 tests.

Residual risk: this does not add live-run controls, new activity types, run pagination, dependency ordering, notifications, or full Automation Studio orchestration parity.

## 2026-05-20 Release Evidence Validator Negative Fixtures

Source: `scripts/ops/test-release-evidence-validators.ps1`, `scripts/ops/validate-ga-evidence.ps1`, `scripts/ops/validate-image-evidence.ps1`.

Outcome: release evidence self-tests now prove GA evidence accepts a valid temp manifest and rejects placeholder values, missing artifacts, absolute paths, path escapes, and missing required fields. Image evidence self-tests now prove valid digest-pinned evidence passes and reject missing production images, extra images, digest mismatches, absolute evidence paths, and path escapes.

Validation: `scripts\ops\test-release-evidence-validators.ps1` passed, and local-only release gate passed. This remains local validation only.

Residual risk: real target-environment production evidence is still absent and production readiness remains blocked.

## 2026-05-20 Data Extension Governance Migration Tests

Source: `services/audience-service/src/test/java/com/legent/audience/repository/DataExtensionWorkspaceMigrationTest.java`, `services/audience-service/src/test/java/com/legent/audience/service/DataExtensionServiceTest.java`.

Outcome: audience-service tests now guard the data-extension workspace-scope migration contracts and service governance rules for same-tenant/workspace relationships, field existence, sendable-field validation, retention clearing, and scoped soft delete behavior.

Validation: focused data-extension tests passed with 14 tests, full `.\mvnw.cmd -pl services/audience-service -am test` passed with 104 reactor tests, JPA config scan found production service defaults at `validate`, Codex system validation passed, repository artifact hygiene passed, and `git diff --check` passed with only CRLF warnings.

Residual risk: static migration contract tests do not replace PostgreSQL/Flyway application proof against target data; target migration evidence remains required before release claims.

## 2026-05-20 Frequency Optimization Deterministic Policy Contract

Source: `services/foundation-service/src/main/java/com/legent/foundation/service/performance/ClosedLoopOptimizationService.java`, `services/foundation-service/src/test/java/com/legent/foundation/service/OptimizationPerformanceServiceTest.java`, `services/campaign-service/src/test/java/com/legent/campaign/service/CampaignSendSafetyServiceTest.java`, `docs/product/ai-governance-optimization-foundation.md`, and `docs/product/salesforce-parity-matrix.md`.

Outcome: foundation performance intelligence now accepts `FREQUENCY` optimization policies and evaluates deterministic frequency readiness with lookback, variant readiness, low-data fallback, saturation category, recommended cap, confidence band, safety-impact reasons, human approval/rollback requirements, and fail-closed gates for suppression, unsubscribe/preference, warmup, rate limit, provider capacity, deliverability, and current frequency-cap ledger safety. Campaign tests now prove tenant/workspace-scoped frequency caps suppress recipients before delivery handoff.

Validation: focused foundation optimization tests passed with 11 tests, focused campaign safety tests passed with 4 tests, full `.\mvnw.cmd -pl services/foundation-service,services/campaign-service -am test` passed with 95 foundation and 80 campaign tests, Codex system validation passed, repository artifact hygiene passed, and `git diff --check` passed with only CRLF warnings.

Residual risk: this is not model-backed engagement frequency optimization, live cadence control, provider capacity evidence, or a production release. External target evidence remains required before parity, deliverability, or throughput claims.

## 2026-05-20 Admin Settings Context Mismatch Fail Closed

Source: `services/foundation-service/src/main/java/com/legent/foundation/service/AdminSettingsService.java`, `services/foundation-service/src/test/java/com/legent/foundation/service/AdminSettingsServiceTest.java`.

Outcome: admin settings validate/apply/reset/impact now reject request-body `workspaceId` and `environmentId` mismatches against `TenantContext`; reset also fails closed when WORKSPACE or ENVIRONMENT scope lacks required context.

Validation: focused `AdminSettingsServiceTest` passed with 6 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 98 foundation-service tests plus upstream shared-module tests, and `git diff --check` passed for touched files with CRLF warnings only.

Residual risk: tenant lifecycle cross-tenant operations and config update/delete raw-ID scoping remain open foundation audit findings.

## 2026-05-20 Config By-ID Tenant Scope

Source: `services/foundation-service/src/main/java/com/legent/foundation/service/ConfigService.java`, `services/foundation-service/src/main/java/com/legent/foundation/repository/ConfigRepository.java`, `services/foundation-service/src/test/java/com/legent/foundation/service/ConfigServiceTest.java`.

Outcome: config update/delete by ID now resolve mutable configs by current tenant and non-deleted status before any mutation, and reject workspace/environment-scoped configs when current `TenantContext` lacks or mismatches the required scope.

Validation: focused `ConfigServiceTest` passed with 8 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 101 foundation-service tests plus upstream shared-module tests, and `git diff --check` passed for touched files with CRLF warnings only.

Residual risk: tenant lifecycle cross-tenant policy, scope-aware config version history, and explicit privileged global-config administration remain separate foundation follow-ups.

## 2026-05-20 Config Create/Upsert Context Mismatch Fail Closed

Source: `services/foundation-service/src/main/java/com/legent/foundation/service/ConfigService.java`, `services/foundation-service/src/test/java/com/legent/foundation/service/ConfigServiceTest.java`.

Outcome: config create/upsert now rejects request-body workspace/environment mismatches before repository lookup or save; explicit workspace/environment scope requires trusted args or matching `TenantContext`.

Validation: focused `ConfigServiceTest` passed with 11 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 109 foundation-service tests plus upstream shared-module tests, and `git diff --check` passed for touched files with CRLF warnings only.

Residual risk: config version-history records still need schema-backed workspace/environment scoping, and tenant lifecycle cross-tenant policy remains a separate platform-admin versus self-tenant decision.

## 2026-05-20 Differentiation Upsert Workspace Exact Match

Source: `services/foundation-service/src/main/java/com/legent/foundation/service/DifferentiationPlatformService.java`, `services/foundation-service/src/test/java/com/legent/foundation/service/DifferentiationPlatformServiceTest.java`.

Outcome: differentiation platform upsert-by-key lookup now uses null-safe exact workspace matching, preventing tenant-scoped or no-workspace requests from selecting workspace-scoped rows before update.

Validation: focused `DifferentiationPlatformServiceTest` passed with 5 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 103 foundation-service tests plus upstream shared-module tests, and `git diff --check` passed for touched files with CRLF warnings only.

Residual risk: list/evaluate query semantics still need endpoint-by-endpoint review before changing tenant-level plus workspace-level visibility; tenant lifecycle policy and config version-history scoping remain separate follow-ups.

## 2026-05-20 Foundation Core Platform Workspace Guards

Source: `services/foundation-service/src/main/java/com/legent/foundation/service/CorePlatformService.java`, `services/foundation-service/src/test/java/com/legent/foundation/service/CorePlatformServiceTest.java`.

Outcome: core-platform team/department creation now rejects workspace/context mismatches; memberships prove workspace-to-organization, workspace-to-business-unit, team, and department ownership; role bindings prove workspace context, team ownership, user membership, and known resource workspace ownership; access grants prove grantee membership; permission groups reject cross-tenant request IDs.

Validation: focused `CorePlatformServiceTest` passed with 28 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 138 foundation-service tests plus upstream shared modules, Codex validation passed, monitor check passed, lease validation passed, and scoped `git diff --check` passed with CRLF warnings only.

Residual risk: role-binding/access-grant list and access-policy preview read paths remain tenant-wide and need a separately leased controller/service slice; nullable permission-list JSON defaults remain a small follow-up.

## 2026-05-20 Foundation Core Platform Read Scope

Source: `services/foundation-service/src/main/java/com/legent/foundation/service/CorePlatformService.java`, `services/foundation-service/src/main/java/com/legent/foundation/controller/CorePlatformController.java`, `services/foundation-service/src/test/java/com/legent/foundation/service/CorePlatformServiceTest.java`, and `services/foundation-service/src/test/java/com/legent/foundation/controller/CorePlatformControllerTest.java`.

Outcome: core-platform role-binding/access-grant lists and access-policy preview now use authenticated roles to preserve tenant-wide reads only for tenant-wide principals while requiring workspace context and exact-workspace predicates for workspace-scoped callers.

Validation: focused `CorePlatformServiceTest,CorePlatformControllerTest` passed with 45 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 150 foundation-service tests plus upstream shared modules, Codex validation passed, monitor check passed, lease validation passed, and scoped `git diff --check` passed with CRLF warnings only.

Residual risk: workspace reads intentionally exclude `workspace_id` null tenant/global rows until inherited visibility semantics are decided; access-policy preview filters returned rows but does not prove principal membership because the endpoint lacks typed principal context.

## 2026-05-20 Foundation Differentiation Evaluate Scope

Source: `services/foundation-service/src/main/java/com/legent/foundation/service/DifferentiationPlatformService.java`, `services/foundation-service/src/test/java/com/legent/foundation/service/DifferentiationPlatformServiceTest.java`, six read-only differentiation scouts, and `services/foundation-service/src/main/resources/db/migration/V13__phase4_differentiation_platform.sql`.

Outcome: decision-policy evaluation, omnichannel simulation, and SLO evaluation now use exact nullable workspace matching, so missing workspace context can no longer wildcard into arbitrary workspace rows or create run/incident records under a workspace the caller did not prove.

Validation: focused `DifferentiationPlatformServiceTest` passed with 9 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 154 foundation-service tests plus upstream shared modules, Codex validation passed, monitor check passed, lease validation passed, and scoped `git diff --check` passed with CRLF warnings only.

Residual risk: service-level null workspace still matches null-workspace tenant/global rows; inherited fallback semantics and list/inventory visibility remain separate product/security decisions.

## 2026-05-20 Foundation Compliance Privacy Request Workspace Scope

Source: `services/foundation-service/src/main/java/com/legent/foundation/service/ComplianceEvidenceService.java`, `services/foundation-service/src/test/java/com/legent/foundation/service/ComplianceEvidenceServiceTest.java`, and six read-only compliance privacy-request scouts.

Outcome: privacy-request status updates now require current workspace context and mutate by tenant+workspace+ID before audit evidence is written, with denial tests proving missing workspace and scoped-row-missing paths do not create audit records.

Validation: focused `ComplianceEvidenceServiceTest` passed with 5 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 157 foundation-service tests plus upstream shared modules, Codex validation passed, monitor check passed, lease validation passed, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only.

Residual risk: nullable or tenant-global privacy request mutation remains blocked until a specific admin policy is designed; list/export nullable workspace behavior was not changed in this slice.

## 2026-05-20 Foundation Permission List JSON Defaulting

Source: `services/foundation-service/src/main/java/com/legent/foundation/service/CorePlatformService.java`, `services/foundation-service/src/test/java/com/legent/foundation/service/CorePlatformServiceTest.java`, and six read-only permission-list scouts.

Outcome: role definition, permission group, and delegated access grant creation now serialize omitted permission lists as JSON arrays `[]` while preserving non-empty arrays and object-shaped metadata defaults.

Validation: focused `CorePlatformServiceTest` passed with 41 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 161 foundation-service tests plus upstream shared modules, V6 migration diff check passed, Codex validation passed, monitor check passed, lease validation passed, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only.

Residual risk: existing rows with malformed permission JSON shapes are not backfilled; strict clients that depended on `{}` for omitted permissions need compatibility review.

## 2026-05-20 Public Contact Admin Platform-Admin Only

Source: `services/foundation-service/src/main/java/com/legent/foundation/controller/AdminContactRequestController.java`, `services/foundation-service/src/test/java/com/legent/foundation/controller/AdminContactRequestControllerSecurityTest.java`.

Outcome: global public contact request admin list/status access now requires `PLATFORM_ADMIN`, preventing ordinary tenant/org admins from viewing or changing public-contact PII rows in a table without tenant/workspace ownership.

Validation: focused `AdminContactRequestControllerSecurityTest` and `PublicContactServiceTest` passed with 6 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 106 foundation-service tests plus upstream shared-module tests, and `git diff --check` passed for touched files with CRLF warnings only.

Residual risk: tenant/workspace-scoped contact inbox remains a schema/product-design follow-up; additional service-level PII/status tests can be expanded separately.

## 2026-05-20 Tenant Get Self Scope

Source: `services/foundation-service/src/main/java/com/legent/foundation/service/TenantService.java`, `services/foundation-service/src/test/java/com/legent/foundation/service/TenantServiceTest.java`.

Outcome: tenant reads by ID now require the requested tenant ID to match `TenantContext` before repository lookup, so known cross-tenant IDs are hidden and missing tenant context fails closed.

Validation: focused `TenantServiceTest` passed with 3 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 112 foundation-service tests plus upstream shared-module tests, and `git diff --check` passed for touched files with CRLF warnings only.

Residual risk: tenant list, get-by-slug, and lifecycle mutations still need a platform-admin versus self-tenant policy decision before safe local implementation.

## 2026-05-20 Audience Contact Data Designer Preview Governance

Source: `services/audience-service/src/main/java/com/legent/audience/dto/DataExtensionDto.java`, `services/audience-service/src/main/java/com/legent/audience/service/DataExtensionService.java`, `services/audience-service/src/main/java/com/legent/audience/service/SegmentService.java`, `services/audience-service/src/test/java/com/legent/audience/service/DataExtensionServiceTest.java`, and `services/audience-service/src/test/java/com/legent/audience/service/SegmentServiceTest.java`.

Outcome: data-extension contracts now cascade nested validation, cap preview/import/relationship payload shapes, enforce supported relationship cardinalities, field type and primary-key compatibility, lock effective sendable-key changes once records exist, require sendable fields to be required, reject relationship path preview fields until joins are designed, validate preview fields/filters/sorts up front, sort before projection, and block unsupported data-extension relationship metadata in segment conditions.

Validation: focused `DataExtensionServiceTest,SegmentServiceTest` passed with 31 tests, full `.\mvnw.cmd -pl services/audience-service -am test` passed with 122 audience-service tests plus upstream shared modules, Codex system validation passed, repository artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only.

Residual risk: this was a no-schema backend hardening slice. First-class provenance/classification/audit tables, import source metadata, indexed relationship execution, frontend relationship designer controls, and target migration proof remain follow-up work.

## 2026-05-20 Audience Contact Data Designer Governance Metadata

Source: `services/audience-service/src/main/java/com/legent/audience/domain/DataExtension.java`, `DataExtensionField.java`, `DataExtensionGovernanceAudit.java`, `DataExtensionDto.java`, `DataExtensionService.java`, `DataExtensionController.java`, `DataExtensionGovernanceAuditRepository.java`, `services/audience-service/src/main/resources/db/migration/V18__data_extension_governance_metadata.sql`, `DataExtensionServiceTest.java`, `DataExtensionGovernanceMigrationTest.java`, and `AudienceControllerRbacTest.java`.

Outcome: data extensions now carry first-class source/provenance metadata, data classification, governance review fields, field-level classification, and a tenant/workspace-scoped governance audit trail for create, governance update, sendable config, retention, relationships, and soft delete changes.

Validation: focused `DataExtensionServiceTest,DataExtensionGovernanceMigrationTest,AudienceControllerRbacTest` passed with 32 tests, full `.\mvnw.cmd -pl services/audience-service -am test` passed with 131 audience-service tests plus upstream shared modules, frontend lint passed, Codex validation passed, lease validation passed, and `git diff --check` passed with CRLF warnings only.

Residual risk: this is local schema/API evidence only. Target Flyway migration proof against representative legacy rows, import-source population depth, subscriber/contact provenance, frontend governance drawer controls, indexed relationship execution, and Contact Builder parity claims remain follow-up work.

## 2026-05-20 Email Governance Policy Objects

Source: `services/content-service/src/main/java/com/legent/content/domain/SendGovernancePolicy.java`, `services/content-service/src/main/java/com/legent/content/controller/SendGovernancePolicyController.java`, `services/content-service/src/main/java/com/legent/content/service/SendGovernancePolicyService.java`, `services/campaign-service/src/main/java/com/legent/campaign/service/CampaignLaunchReadinessGate.java`, `services/campaign-service/src/main/java/com/legent/campaign/client/ContentServiceClient.java`, `services/content-service/src/main/resources/db/migration/V10__send_governance_policies.sql`, and `services/campaign-service/src/main/resources/db/migration/V15__campaign_send_governance_policy.sql`.

Outcome: content-service now provides tenant/workspace-scoped send governance policies, campaigns persist selected policy IDs, and campaign launch/preflight/direct send readiness fails closed unless content-service verifies the policy and its commercial, sender, domain, provider, active, and retention controls.

Validation: focused content and campaign tests passed, full `.\mvnw.cmd -pl services/content-service,services/campaign-service,services/delivery-service -am test` passed, route validation passed, repo artifact hygiene passed, Codex validation passed, and `git diff --check` passed with CRLF warnings only.

Residual risk: this is a local governance contract only; delivery-owned immutable policy snapshots, UI management depth, target migration evidence, production route-edge proof, and any compliance/parity/throughput claims remain follow-up work.

No product fix entries exist in the fresh memory baseline.

Current non-product success:
- 2026-05-22: Production alert runbook links are now locally validated. Source problem: `prometheus-alerts.yml` referenced `docs/operations/production-hardening-runbook.md` and anchors that did not exist, and `validate-production-overlay.ps1` did not check local `runbook_url` targets. Changed files: `docs/operations/production-hardening-runbook.md` and `scripts/ops/validate-production-overlay.ps1`. Validation: production overlay validation passed, negative missing-runbook and missing-anchor fixtures failed as expected, local-only release gate passed, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: this is local runbook hygiene only; target alert firing, routing, dashboards, and on-call handoff evidence remain required before GA claims.
- 2026-05-22: GA and image release evidence manifests now have enforced schema-version contracts. Source problem: GA and image evidence templates emitted `schemaVersion = 1`, but their validators accepted manifests without a version contract, unlike the production egress validator. Changed files: `scripts/ops/validate-ga-evidence.ps1`, `scripts/ops/validate-image-evidence.ps1`, `scripts/ops/test-release-evidence-validators.ps1`, and `docs/operations/ga-evidence-matrix.md`. Validation: release evidence validator self-test passed with the 120s child-process timeout, local-only release gate passed, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: this is local release-input hardening only; strict production promotion still requires real target artifacts.
- 2026-05-21: Route validation now discovers service-internal controller routes from source. Source problem: public-edge denial validation used a manually maintained internal route list, so future Spring controller routes under broad public prefixes could be missed unless the list was updated by hand. Changed files: `scripts/ops/validate-route-map.ps1`. Validation: route validation passed for 49 route-map entries and 8 source-discovered service-internal routes, negative temporary-config checks failed as expected for a removed deny and a new uncovered internal controller route, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: ingress-nginx snippet enforcement still needs target cluster evidence before production promotion.
- 2026-05-21: Audience internal routes now have focused Spring security-chain coverage. Source problem: audience-service allowed the internal query-activity and import-start routes through Spring security so controller-level internal credential guards could enforce access, but there was no focused security-chain regression test proving those exact routes remained allowed while normal audience routes stayed authenticated. Changed files: `SecurityConfigTest.java`. Validation: focused `SecurityConfigTest` passed with 5 tests, full `.\mvnw.cmd -pl services/audience-service -am test` passed with 136 audience-service tests plus upstream shared modules, Codex validation passed, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: future audience internal routes must add the same allowed/denied security-chain coverage and public edge validation where externally reachable.
- 2026-05-21: Frontend context storage comments now match behavior. Source problem: `frontend/src/lib/auth.ts` comments implied tenant context was only in HTTP-only cookies even though tenant/workspace/environment IDs are intentionally cached in localStorage as non-sensitive request-header context. Changed files: `auth.ts`. Validation: frontend lint passed, Codex validation passed, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: future auth/context bootstrap changes must preserve the distinction between cookie-backed session credentials and local routing context.
- 2026-05-21: Release evidence validator self-test child processes are now bounded. Source problem: `test-release-evidence-validators.ps1` launched negative-fixture validators with `Start-Process -Wait`, so a stalled child validator could hang the self-test and leave child processes behind. Changed files: `scripts/ops/test-release-evidence-validators.ps1`. Validation: release evidence validator self-test passed, local-only release gate passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: this improves local/CI self-test safety only; it is not production evidence and strict promotion still requires real target artifacts.
- 2026-05-20: `.codex` autonomous organization was rebuilt and hardened as a project operating system. Evidence: changed files under `.codex/`, root operating docs, `docs/operations/`, `docs/audits/`, `docs/product/`, and `scripts/ops/`. Validation included Codex system validation, route map, env example, production overlay, repo artifact hygiene, egress evidence template, release evidence validator self-test, local release gate, Compose config with `.env.example`, Kustomize render, JSON parsing, PowerShell parser checks, and `git diff --check`.
- 2026-05-20: Codex state/dashboard convergence fixed stale team-state queue mirrors, dashboard Markdown code-span rendering for work item IDs, and validation coverage for ready/backlog/active mirror drift. Validation: monitor regenerate, Codex system validation, cleanup dry run, and scoped `.codex` `git diff --check` passed. Residual risk: manual queue edits must use Codex state utilities or keep team-state mirrors synchronized.
- 2026-05-20: Production egress evidence validation now rejects placeholder evidence. Source problem: the checked-in egress evidence template passed validation with `example-*` fields and RFC documentation CIDR values. Changed files: `validate-production-egress-evidence.ps1`, `test-release-evidence-validators.ps1`, `ga-evidence-matrix.md`, release command docs, validation gates, and dated report errata. Validation: release evidence self-test passed; direct template validation failed as expected; local release gate, Codex validation, and `git diff --check` passed. Residual risk: real production egress evidence and generated policy inclusion proof remain separate requirements.
- 2026-05-20: Strict production egress validation now proves reviewed policy render inclusion. Source problem: reviewed egress evidence could be validated without proving the generated reviewed external egress NetworkPolicy rendered into the production manifest, and stale generated policy artifacts were not hash-tied to the evidence. Changed files: `write-production-egress-policy.ps1`, `validate-production-egress-policy-render.ps1`, `validate-production-egress-evidence.ps1`, `release-gate.ps1`, and `test-release-evidence-validators.ps1`. Validation: release evidence self-test, production overlay validation, production Kustomize render, local release gate, Codex validation, repo artifact hygiene, and `git diff --check`. Residual risk: target-environment egress, image, GA, load, restore, CI/security, TLS/admission, and monitoring evidence still block production promotion.

Current product success:
- 2026-05-22: Latest audit safe local follow-ups are complete. Source problem: the read-only audit found eight unblocked local gaps across release/SRE validation, delivery legacy workspace ownership, identity password reset scoping, tracking publisher API surface, SES endpoint handling, subscriber UI contracts, and landing-page sanitizer route coverage. Changed files: `prometheus-alerts.yml`, `production-hardening-runbook.md`, release validation scripts, `V17__delivery_legacy_workspace_mapping_guard.sql`, `AwsSesProviderAdapter.java`, identity forgot-password service/controller/DTO/repository files and tests, `TrackingEventPublisher.java`, subscriber page/E2E, and landing-page E2E. Validation: focused delivery, identity, and tracking tests passed; full delivery+identity+tracking backend gate passed with 95 target-module tests plus shared modules; frontend lint, production build, subscriber/landing-page Playwright, sanitizer Playwright, production overlay, release evidence self-test, Compose config with `.env.example`, local-only release gate, artifact hygiene, Codex validation, and `git diff --check` passed. Residual risk: this is local source and test evidence only; production promotion, target Flyway data review, provider capacity, monitoring handoff, and high-volume evidence remain blocked externally.
- 2026-05-22: Reviewed AI content assistance evidence can now be applied to content drafts under a hash-only local contract. Source problem: foundation-service had draft-only AI policy/audit governance, but content-service did not yet accept reviewed draft evidence or fail closed before publish/test-send when AI assistance metadata was unresolved. Changed files: `TemplateWorkflowDto.java`, `TemplateWorkflowController.java`, `TemplateWorkflowService.java`, `TemplateTestSendService.java`, `AiContentAssistanceMetadataSupport.java`, `TemplateWorkflowServiceTest.java`, `TemplateTestSendServiceTest.java`, and `AiContentAssistanceGovernanceServiceTest.java`. Validation: focused foundation governance test passed with 10 tests, focused content workflow/test-send tests passed with 5 tests, full `.\mvnw.cmd -pl services/foundation-service,services/content-service -am test` passed with 59 target-service tests plus upstream shared modules, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: this is a local evidence contract only; model provider calls, cross-service audit verification, generated-draft UX, metering, and AI parity claims remain separate reviewed work.
- 2026-05-22: Audience tracking intelligence consumption now uses a dedicated batch Kafka listener. Source problem: audience-service consumed high-volume `tracking.ingested` intelligence events one record at a time while tracking-service already had a batch listener for the same topic. Changed files: `AudienceIntelligenceConsumer.java`, `AudienceKafkaConsumerConfig.java`, `AudienceIntelligenceConsumerTest.java`, and `AudienceKafkaConsumerConfigTest.java`. Validation: focused `AudienceIntelligenceConsumerTest,AudienceKafkaConsumerConfigTest,SubscriberIntelligenceServiceTest` passed with 14 tests, full `.\mvnw.cmd -pl services/audience-service -am test` passed with 148 audience-service tests plus upstream shared modules, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: this is local consumer hardening only; target Kafka lag, DB contention, and subscriber-intelligence throughput evidence remain required before scale claims.
- 2026-05-22: Content-service protected read endpoints now require read RBAC. Source problem: many authenticated content GET routes for Email Studio, templates, content blocks, template versions, approvals, and emails lacked method-level `@PreAuthorize`, while tests only enforced unsafe mappings. Changed files: `EmailStudioController.java`, `TemplateController.java`, `ContentBlockController.java`, `TemplateVersionController.java`, `TemplateWorkflowController.java`, `EmailController.java`, and `ContentControllerRbacTest.java`. Validation: focused `ContentControllerRbacTest` passed with 7 tests, full `.\mvnw.cmd -pl services/content-service -am test` passed with 54 content-service tests plus upstream shared modules, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: target auth behavior remains normal release evidence; future content GET routes must keep explicit public/internal exceptions.
- 2026-05-22: Audience import status lifecycle UI now matches backend states. Source problem: the imports list mapped `RUNNING` instead of backend `VALIDATING`/`PROCESSING`, details polling ignored `VALIDATING`, and the wizard treated `CANCELLED` as nonterminal plus `FAILED`/`COMPLETED_WITH_ERRORS` as one generic issue state. Changed files: `frontend/src/app/(workspace)/audience/imports/page.tsx`, `new/page.tsx`, `[id]/page.tsx`, and `frontend/tests/e2e/audience-imports.spec.ts`. Validation: frontend lint passed, frontend production build passed, targeted Chromium Playwright import lifecycle spec passed with 6 tests, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: this is mocked local UI validation only; target import throughput, parser behavior, storage limits, and backend worker evidence remain separate.
- 2026-05-22: Subscriber by-ID reads and mutations are now tenant/workspace scoped. Source problem: `SubscriberService.getById`, `update`, and `delete` used raw `subscriberRepository.findById` before filtering the loaded row, while the repository already had a scoped lookup. Changed files: `SubscriberService.java` and `SubscriberServiceTest.java`. Validation: focused `SubscriberServiceTest` passed with 9 tests, full `.\mvnw.cmd -pl services/audience-service -am test` passed with 142 audience-service tests plus upstream shared modules, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: cached subscriber reads still rely on the existing tenant-aware cache key generator; preserve that boundary if cache keys change.
- 2026-05-22: Delayed automation workflow resumes are now workspace scoped. Source problem: `DelayNodeHandler` stored tenant/workspace on Quartz jobs, but `WorkflowQuartzJob` ignored that scope and `WorkflowEngine.resumeInstance` loaded instances by raw ID before setting context from the row. Changed files: `WorkflowEngine.java`, `WorkflowQuartzJob.java`, `WorkflowEngineTest.java`, and `WorkflowQuartzJobTest.java`. Validation: focused workflow engine/Quartz tests passed with 9 tests, full `.\mvnw.cmd -pl services/automation-service -am test` passed with 82 automation-service tests plus upstream shared modules, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: broader scheduled/retry/recovery automation paths should preserve scoped resume contracts when expanded.
- 2026-05-22: Deliverability internal suppression routes now have service security-chain coverage. Source problem: the deliverability service intentionally allows internal suppression list/check paths through the HTTP filter chain so controller-level internal credential guards can run, but coverage existed only at controller/reflection level. Changed file: `SecurityConfigTest.java`. Validation: focused deliverability security-chain test passed with 5 tests, full `.\mvnw.cmd -pl services/deliverability-service -am test` passed with 64 deliverability-service tests plus upstream shared modules, route-map validation passed for 49 routes and 8 source-discovered internal routes, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: this is local test coverage only; target service and edge behavior still require normal release evidence before promotion.
- 2026-05-22: Identity invitation listing is now workspace scoped. Source problem: `AuthService.listInvitations` used a tenant-only invitation repository query, and `AuthController.listInvitations` did not require a workspace-bearing principal before returning invitation metadata. Changed files: `AuthController.java`, `AuthService.java`, `AuthInvitationRepository.java`, `AuthServiceTest.java`, and `AuthControllerTest.java`. Validation: focused identity service/controller tests passed with 32 tests, full `.\mvnw.cmd -pl services/identity-service -am test` passed with 51 identity-service tests plus upstream shared modules, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: any future tenant-global invitation administration must be a separately designed privileged path.
- 2026-05-22: Audience import row-error retention is now bounded during processing. Source problem: `ImportProcessingService` appended every per-row error detail across chunks and only truncated at final completion, so high-error imports could retain far more than `AppConstants.IMPORT_MAX_ERRORS` in memory. Changed files: `ImportProcessingService.java` and `ImportProcessingServiceTest.java`. Validation: focused import tests passed with 9 tests, full `.\mvnw.cmd -pl services/audience-service -am test` passed with 138 audience-service tests plus upstream shared modules, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: target import-load evidence is still required before high-volume import readiness claims.
- 2026-05-21: Campaign send-job checkpoints and resume recovery are now workspace scoped. Source problem: `SendJobCheckpointingService` used tenant-only checkpoint lookups and created resumed jobs without carrying workspace ownership, while checkpoint/resume/recovery tables lacked enforced workspace scope. Changed files: `SendJobCheckpointingService.java`, `SendJobCheckpointRepository.java`, `SendJobCheckpoint.java`, `V16__send_job_checkpoint_workspace_scope.sql`, and `SendJobCheckpointingServiceTest.java`. Validation: focused campaign checkpoint/send-execution/consumer/scheduler tests passed with 54 tests, full `.\mvnw.cmd -pl services/campaign-service -am test` passed with 106 campaign-service tests plus upstream shared modules, repo artifact hygiene passed, JPA config scan confirmed production services still default to validate, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: target Flyway migration application and legacy checkpoint/resume row ownership review remain required before production promotion.
- 2026-05-21: Legacy deliverability reputation scores are now tenant/workspace scoped. Source problem: `ReputationController` fell back from scoped domain reputation to a domain-only `reputation_scores` lookup, and `ReputationService` wrote new legacy rows without tenant/workspace ownership even though the table already had scope columns. Changed files: `ReputationController.java`, `ReputationScore.java`, `ReputationScoreRepository.java`, `ReputationService.java`, `V11__reputation_score_workspace_scope.sql`, `ReputationControllerTest.java`, `ReputationScoreRepositoryTest.java`, and `ReputationServiceTest.java`. Validation: focused reputation/RBAC tests passed with 19 tests, full `.\mvnw.cmd -pl services/deliverability-service -am test` passed with 59 deliverability-service tests plus upstream shared modules, repo artifact hygiene passed, JPA config scan confirmed production services still default to validate, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: target Flyway migration application and legacy-row ownership review remain required before production promotion.
- 2026-05-21: Data-extension governance controls are now visible and editable in the frontend. Source problem: backend data-extension governance metadata and audit endpoints existed locally, but the Audience Data Extensions screen still showed only basic table/create fields, so operators could not set or review source/classification/audit evidence from the UI. Changed files: `data-extensions/page.tsx`, `types.ts`, `audience-data-extensions.spec.ts`. Validation: frontend lint passed, frontend production build passed, targeted Chromium Playwright governance spec passed with 1 test, Codex validation passed, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: target migration proof, import/contact provenance population, and indexed relationship execution remain separate requirements before Contact Builder parity or production claims.
- 2026-05-21: Tracking outbox enqueue no longer publishes inline by default. Source problem: `TrackingOutboxService.enqueue` saved a durable outbox row, then immediately published that same row after transaction commit or inline when no transaction synchronization was active, coupling public tracking ingestion responses to Kafka health despite having an outbox poller. Changed files: `TrackingOutboxService.java`, `TrackingOutboxServiceTest.java`. Validation: focused tracking outbox/ingestion/publisher tests passed with 14 tests, full `.\mvnw.cmd -pl services/tracking-service -am test` passed with 72 tests and 7 expected Testcontainers skips, Codex validation passed, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: target tracking ingress latency, outbox depth/age, Kafka lag, and ClickHouse evidence remain required before throughput or production readiness claims.
- 2026-05-21: Delivery replay queue processing is now bounded and claimed atomically. Source problem: `DeliveryOperationsService.processReplayQueue` fetched every due replay row, saved `PROCESSING` without a compare-and-claim, and rebuilt replay payloads without preserving the source `contentReference`. Changed files: `DeliveryOperationsService.java`, `DeliveryReplayQueueRepository.java`, `DeliveryOperationsServiceWorkspaceScopeTest.java`. Validation: focused delivery operations tests passed with 8 tests, full `.\mvnw.cmd -pl services/delivery-service -am test` passed with 90 delivery-service tests plus upstream shared modules, Codex validation passed, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: target multi-worker replay, database contention, provider capacity, and live queue-lag evidence remain required before production readiness or throughput claims.
- 2026-05-21: Campaign delivery feedback counters are now updated atomically. Source problem: `CampaignEventConsumer` mutated loaded `SendJob` and `SendBatch` entities for sent/failed feedback counters, risking lost updates under concurrent feedback events. Changed files: `CampaignEventConsumer.java`, `SendJobRepository.java`, `SendBatchRepository.java`, `CampaignEventConsumerTest.java`, `CampaignFeedbackCounterRepositoryTest.java`. Validation: focused campaign event/repository tests passed with 25 tests, full `.\mvnw.cmd -pl services/campaign-service -am test` passed with 99 campaign-service tests plus upstream shared modules, Codex validation passed, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only. Residual risk: target Kafka replay, database contention, and queue-lag evidence remain required before throughput or production readiness claims.
- 2026-05-20: Feature flag by-ID operations are now tenant-scoped. Source problem: `FeatureFlagService` used raw `findById` for get/update/delete and `FeatureFlagController.getFlag` lacked `@PreAuthorize`. Changed files: `FeatureFlagController.java`, `FeatureFlagService.java`, `FeatureFlagRepository.java`, `FeatureFlagServiceTest.java`, `FeatureFlagControllerSecurityTest.java`. Validation: `.\mvnw.cmd -pl services/foundation-service -am test` passed. Residual risk: global feature flag administration, if needed, must be designed as an explicit privileged workflow rather than using tenant-scoped by-ID endpoints.
- 2026-05-20: Public edge now blocks known service-internal routes. Source problem: broad Nginx/ingress prefixes exposed internal token-protected content, audience, and deliverability endpoints. Changed files: `config/nginx/nginx.conf`, `infrastructure/kubernetes/ingress/ingress.yml`, `scripts/ops/validate-route-map.ps1`. Validation: `scripts\ops\validate-route-map.ps1`, `kubectl kustomize infrastructure\kubernetes\overlays\production`, and `docker compose config --quiet` passed. Residual risk: any newly added `/internal` route must be added to the validator deny list.
- 2026-05-20: Audience resolution suppression checks are now candidate-scoped. Source problem: `DeliverabilityServiceClient` fetched every deliverability suppression for a tenant/workspace and filtered client-side. Changed files: `DeliverabilityServiceClient.java`, `AudienceResolutionConsumer.java`, `SuppressionController.java`, `SuppressionListRepository.java`, `V10__suppression_bulk_lookup_index.sql`, `nginx.conf`, `ingress.yml`, `validate-route-map.ps1`, and focused audience/deliverability tests. Validation: `.\mvnw.cmd -pl services/audience-service,services/deliverability-service -am test`, route validation, Kustomize render, Compose config, repo artifact hygiene, production overlay validation, Codex validation, and `git diff --check` passed. Residual risk: live high-volume proof still requires target-like load evidence and provider capacity.
- 2026-05-20: Delivery feedback publication is now durable. Source problem: `DeliveryEventPublisher` fired Kafka futures without awaiting or storing them, so `message_logs` could commit `SENT`/`FAILED`/`PENDING` state while campaign/audience/deliverability feedback never arrived. Changed files: `DeliveryApplication.java`, `DeliveryFeedbackOutboxEvent.java`, `DeliveryFeedbackMessage.java`, `DeliveryEventPublisher.java`, `DeliveryFeedbackOutboxEventRepository.java`, `DeliveryFeedbackOutboxService.java`, `DeliveryOrchestrationService.java`, `V15__delivery_feedback_outbox_events.sql`, `DeliveryEventPublisherTest.java`, `DeliveryFeedbackOutboxServiceTest.java`, `DeliveryOrchestrationServiceTest.java`. Validation: focused delivery tests, `.\mvnw.cmd -pl services/delivery-service -am test`, Codex validation, repo artifact hygiene, and `git diff --check` passed. Residual risk: live depth/oldest-age alerting, retention cleanup, and target-like outbox throughput evidence are still needed before high-volume claims.
- 2026-05-20: Campaign send handoff now satisfies the `email.send.requested` content-reference contract. Source problem: the default send path could publish inline rendered content without `contentReference`, while shared Kafka and delivery runtime require a durable reference. Changed files: `SendExecutionService.java`, `application.yml`, `SendExecutionServiceTest.java`, `EventContractValidatorTest.java`. Validation: focused campaign tests, focused shared Kafka tests, `.\mvnw.cmd -pl services/campaign-service,shared/legent-kafka -am test`, Codex validation, repo artifact hygiene, and `git diff --check` passed. Residual risk: target-like proof is still needed for content snapshot write/fetch capacity and production high-volume inline fallback policy.
- 2026-05-20: Shared Kafka DLQ routing is no longer pinned to partition 0. Source problem: `DeadLetterPublishingRecoverer` always sent failed listener records to `kafka.dead-letter` partition 0 and the local Compose topic definition created a one-partition DLQ. Changed files: `KafkaConsumerConfig.java`, `KafkaTopicConfig.java`, `KafkaConsumerConfigTest.java`, `KafkaTopicConfigTest.java`, `docker-compose.yml`. Validation: focused Kafka config tests, `.\mvnw.cmd -pl shared/legent-kafka -am test`, Compose config, drift scan for fixed partition-0/one-partition DLQ definitions, Codex validation, repo artifact hygiene, and `git diff --check` passed. Residual risk: existing local Kafka volumes may need topic alteration/recreation; production external Kafka partition/retention/replication evidence and DLQ observability remain required.
- 2026-05-20: Journey Builder node publish/runtime contract now fails closed. Source problem: the frontend exposed broad journey node types while live automation runtime supported only `ENTRY_TRIGGER`, `SEND_EMAIL`, `DELAY`, `CONDITION`, and `END`; unsupported nodes could be treated as generic next-step movement or bypass publish validation through active-version edits, rollback, resume, or malformed frontend validation responses. Changed files: `WorkflowStudioService.java`, `WorkflowGraphValidator.java`, `WorkflowEngine.java`, `GenericNodeHandler.java`, `EntryTriggerNodeHandler.java`, `WorkflowEngineTest.java`, `WorkflowStudioServiceTest.java`, `JourneyBuilder.tsx`, `NodeEditorModal.tsx`, `journey-node-contract.ts`, `page.tsx`, `automation-api.ts`, `automation-builder.spec.ts`. Validation: focused automation tests, `.\mvnw.cmd -pl services/automation-service -am test`, frontend lint, frontend production build, targeted Playwright automation-builder spec, Codex validation, and `git diff --check` passed. Residual risk: broader journey node families remain draft-only until each runtime capability is implemented and validated separately.
- 2026-05-20: Tracking ingress route-limit posture is now explicit and validator-backed. Source problem: local Nginx gave `/api/v1/tracking` a dedicated elevated tracking limit, but Kubernetes grouped tracking, analytics, and websocket routes without an effective community ingress-nginx rate annotation. Changed files: `ingress.yml`, `validate-route-map.ps1`, `ga-evidence-matrix.md`, `performance-bottlenecks.md`, `technical-debt.md`, `unresolved-risks.md`. Validation: route validation, production/global Kustomize renders, production overlay validation, Codex validation, and `git diff --check` passed. Residual risk: configured limits do not prove target ingress-controller behavior or downstream tracking ingestion capacity; live evidence remains required.
- 2026-05-20: SSO tenant cookies now match normal auth HTTP-only posture. Source problem: `SsoController` created `legent_tenant_id` without `HttpOnly` while normal auth marked the tenant cookie HTTP-only. Changed files: `SsoController.java`, `SsoControllerTest.java`. Validation: focused identity controller tests, `.\mvnw.cmd -pl services/identity-service -am test`, Codex validation, repo artifact hygiene, and `git diff --check` passed. Residual risk: future SSO callback variants must keep using the shared cookie helper or equivalent parity tests.
- 2026-05-20: Delivery provider configuration is now workspace-isolated. Source problem: SMTP providers, routing rules, health status/checks, capacity profiles, failover drills, and provider selection could rely on tenant/provider IDs without enforcing workspace ownership. Changed files: delivery provider/routing/health repositories, `ProviderController.java`, `ProviderSelectionStrategy.java`, `DeliveryOrchestrationService.java`, `ProviderHealthMonitoringService.java`, `ProviderCapacityService.java`, `V16__smtp_provider_workspace_scope.sql`, and focused delivery tests. Validation: focused provider isolation tests, focused migration/health/capacity tests, `.\mvnw.cmd -pl services/delivery-service -am test`, Codex validation, repo artifact hygiene, and `git diff --check` passed. Residual risk: legacy rows backfilled to `workspace-default` require target data review before production promotion.
- 2026-05-20: AI content assistance governance now has first-class draft-only policy and audit persistence. Source problem: AI governance was documented, but no tenant/workspace policy/audit enforcement existed for content assistance. Changed files: `AiContentAssistanceGovernanceService.java`, AI content assistance DTOs, `V16__ai_content_assistance_governance.sql`, `PerformanceIntelligenceController.java`, `CorePlatformRepository.java`, `PerformanceIntelligenceSummaryService.java`, `AiContentAssistanceGovernanceServiceTest.java`, and product docs. Validation: focused AI content assistance governance tests and `.\mvnw.cmd -pl services/foundation-service,services/content-service -am test` passed. Residual risk: model-provider calls, generated content application, content-service publish/test-send AI review hooks, and parity claims remain separate follow-ups.

Future entries must include:
- source problem,
- changed files,
- validation commands,
- residual risk,
- release note impact.
