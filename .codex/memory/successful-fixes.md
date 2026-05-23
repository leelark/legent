# Successful Fixes

Fresh baseline date: 2026-05-20.

## 2026-05-23 Backend Validation Stability

Source: `services/audience-service/src/test/java/com/legent/audience/client/DeliverabilityServiceClientTest.java` and `services/campaign-service/src/test/java/com/legent/campaign/service/SendExecutionServiceTest.java`.

Outcome: the deliverability client HTTP fixture now uses an explicit server executor, and campaign async render/publish tests use bounded completion assertions instead of brittle one-second preemptive timeouts. This keeps the same behavioral coverage while making the full parallel Maven reactor stable under local load.

Validation: focused `.\mvnw.cmd -pl services/audience-service,services/campaign-service -am "-Dtest=DeliverabilityServiceClientTest,SendExecutionServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed, release evidence validator self-test passed, and full `.\mvnw.cmd test` passed.

Residual risk: this is local test stability only. It does not replace target runtime evidence, provider capacity, live load, or strict release evidence.

## 2026-05-23 Tracking Reconciliation Raw Count Dedupe

Source: `services/tracking-service/src/main/java/com/legent/tracking/service/AnalyticsService.java`, `services/tracking-service/src/test/java/com/legent/tracking/service/AnalyticsServiceTest.java`, and existing ClickHouse rollup dedupe tests.

Outcome: campaign reconciliation raw counts now aggregate from a canonical raw-events subquery grouped by tenant, workspace, event type, and event ID. This aligns reconciliation with campaign-day rollup refresh semantics so duplicate raw rows do not inflate local mismatch checks.

Validation: focused `.\mvnw.cmd -pl services/tracking-service "-Dtest=AnalyticsServiceTest,AnalyticsControllerTest,ClickHouseRollupServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with 23 tests, and scoped `git diff --check` passed with CRLF warnings only.

Residual risk: this is query-shape evidence only. Physical raw-event dedupe, live ClickHouse duplicate-rate/reconciliation evidence, and Docker/PostgreSQL idempotency proof remain external blockers for the broader tracking readiness item.

## 2026-05-23 Frontend Auth Context Allowlist

Source: `frontend/src/lib/api-client.ts`, `frontend/src/lib/auth-api.ts`, and `frontend/tests/e2e/api-client-context.spec.ts`.

Outcome: the frontend no longer suppresses tenant/workspace/environment headers for every `/auth/**` credentialed request. Context-free auth is now an explicit allowlist for login, signup, session, refresh, logout, account contexts, and context switch. Public forgot/reset flows remain credentialless through `postPublic`, while workspace auth actions such as onboarding, logout-all, and credentialed forgot-password calls carry tenant/workspace/environment headers.

Validation: `npm run lint`, `npm run build:ci`, and targeted Chromium Playwright `api-client-context.spec.ts`, `admin.spec.ts`, and `marketing.spec.ts` passed with 33 tests. Scoped `git diff --check` passed with CRLF warnings only.

Residual risk: this is local frontend/client validation only. Backend authorization remains authoritative, and credentialed cross-browser/session evidence remains part of normal release validation.

## 2026-05-23 Kafka High-Volume Topic Config Coverage

Source: `shared/legent-kafka/src/main/java/com/legent/kafka/config/KafkaTopicConfig.java`, `shared/legent-kafka/src/test/java/com/legent/kafka/config/KafkaTopicConfigTest.java`, and `shared/legent-kafka/src/main/java/com/legent/kafka/producer/EventPublisher.java`.

Outcome: local Kafka topic configuration now explicitly declares every topic in the shared publisher high-volume set, including audience-resolution request, send completion/failure, batch completion, subscriber lifecycle, workflow trigger, and delivery/tracking feedback topics. A focused drift test now compares configured local source topics against `EventPublisher.highVolumeTopics()`.

Validation: focused `.\mvnw.cmd -pl shared/legent-kafka "-Dtest=KafkaTopicConfigTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with 4 tests, and scoped `git diff --check` passed with CRLF warnings only.

Residual risk: this is local topic-bean coverage only. Production broker partition counts, replication, retention, ACLs, and managed-topic policy evidence remain external release requirements.

## 2026-05-23 Delivery Feedback Outbox Backpressure Toggle

Source: `services/delivery-service/src/main/java/com/legent/delivery/service/DeliveryFeedbackOutboxService.java` and `services/delivery-service/src/test/java/com/legent/delivery/service/DeliveryFeedbackOutboxServiceTest.java`.

Outcome: delivery feedback still persists a durable outbox row, but immediate publish is now controlled by `legent.delivery.feedback-outbox.immediate-publish-enabled` with a safe default of `true`. When disabled, enqueue leaves the event `PENDING` and avoids a send-path publish claim; the scheduled outbox poller remains responsible for publication and retry evidence.

Validation: initial delivery-only Maven run failed before tests on stale upstream shared classes; rerun with `-am` passed focused `DeliveryFeedbackOutboxServiceTest,DeliveryOrchestrationServiceTest` with 23 tests. Scoped `git diff --check` passed with CRLF warnings only.

Residual risk: this is local backpressure control evidence only. Target poller throughput, Kafka lag, provider send load, outbox retention/cleanup, and alert tuning remain release/performance evidence.

## 2026-05-23 Tracking Analytics Event Count Window Bound

Source: `services/tracking-service/src/main/java/com/legent/tracking/service/AnalyticsService.java`, `services/tracking-service/src/main/java/com/legent/tracking/controller/AnalyticsController.java`, and focused tracking analytics tests.

Outcome: event-count analytics no longer count all raw events by default. The service applies a default 168-hour window, rejects invalid windows, clamps explicit windows to 31 days, and the HTTP counts endpoint accepts optional `startAt`/`endAt`. WebSocket analytics continues to call the no-arg service method, which now uses the bounded default window.

Validation: focused `.\mvnw.cmd -pl services/tracking-service "-Dtest=AnalyticsServiceTest,AnalyticsControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with 16 tests, and scoped `git diff --check` passed with CRLF warnings only.

Residual risk: this is local query-shape evidence only. Target raw-event volume, timestamp index/selectivity, WebSocket fanout latency, and ClickHouse/runtime analytics evidence remain required before scale or production readiness claims.

## 2026-05-23 Audience Segment Recompute Bounded Membership

Source: `services/audience-service/src/main/java/com/legent/audience/service/SegmentEvaluationService.java` and `services/audience-service/src/test/java/com/legent/audience/service/SegmentEvaluationServiceTest.java`.

Outcome: segment recompute now materializes memberships through bounded keyset pages ordered by subscriber ID. Each page is inserted as its own bounded batch, so large segment recomputes no longer build one full subscriber ID list and one full insert-argument list in memory.

Validation: focused `.\mvnw.cmd -pl services/audience-service "-Dtest=SegmentEvaluationServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with 11 tests, and scoped `git diff --check` passed with CRLF warnings only.

Residual risk: this is local unit/service evidence only. Target segment-volume, database lock/latency, scheduler concurrency, and durable predictive rollback snapshot evidence remain separate from the local fix.

## 2026-05-23 Identity Reset Link Production Safety

Source: `shared/legent-common/src/main/java/com/legent/common/config/RuntimeConfigurationGuard.java`, `services/identity-service/src/main/java/com/legent/identity/event/IdentityEventPublisher.java`, and focused common/identity tests.

Outcome: production profiles now fail closed when `legent.frontend.base-url` is blank, invalid, non-HTTPS, or loopback, preventing reset links from being emitted with unsafe or local frontend origins. Reset email HTML now escapes generated reset URLs before placing them in anchor attributes.

Validation: focused `.\mvnw.cmd -pl shared/legent-common,services/identity-service -am "-Dtest=RuntimeConfigurationGuardTest,IdentityExperienceServiceTest,IdentityEventPublisherTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed, and scoped `git diff --check` passed with CRLF warnings only.

Residual risk: this is local configuration and email-template safety evidence only. Target production configuration, actual email rendering, provider delivery behavior, and strict release evidence remain separate.

## 2026-05-23 Local Readiness Cycle

Source: `frontend/src/lib/auth-api.ts`, `Header.tsx`, frontend Playwright specs, `CampaignAudienceEligibilityMarker.java`, `BatchingService.java`, `SendExecutionService.java`, `SendRateControlService.java`, `DeliverySendReservationRepository.java`, `ClickHouseRollupService.java`, focused backend/frontend tests, and 2026-05-23 checkpoints.

Outcome: the ONE_OVERALL_TEAM cycle completed five local production-readiness slices. Public reset-password now uses credentialless public API helpers without tenant/workspace/environment/request headers. Header workspace switching now preserves selected environment context. Campaign send execution now requires an audience eligibility marker on row-backed and legacy recipient payloads and fails closed before delivery handoff when missing. Delivery rate-control no longer sweeps expired reservations on every open-capacity reservation and bounds cleanup under capacity pressure. ClickHouse campaign-day rollup refresh now dedupes raw events by tenant/workspace/event type/event ID before counting.

Validation: focused frontend lint/build/Playwright gates passed, focused campaign Maven gate passed with 32 tests, focused delivery Maven gate passed with 11 tests, focused tracking Maven gate passed with 24 tests, route-map validation passed, repository artifact hygiene passed, and Codex system validation passed during integration.

Residual risk: this is local evidence only. Strict production release, provider capacity, live load, live ClickHouse behavior, target DB migrations, monitoring handoff, CI/security transcript, restore drill, TLS/admission, and external egress evidence remain blocked or unproven.

## 2026-05-22 Account Recovery, Requeue, Workspace Guards, Parity Docs, And Codex Hygiene

Source: `frontend/src/components/marketing/PublicAuthViews.tsx`, `frontend/src/lib/auth-api.ts`, `frontend/tests/e2e/marketing.spec.ts`, campaign batch retry/checkpoint/orchestration services and tests, `GlobalEnterpriseService.java`, `PerformanceLedgerSupport.java`, product parity docs, Codex audit/checkpoint validation utilities, and 2026-05-22 checkpoints.

Outcome: the latest safe local pool completed six exact-leased slices. Public account recovery now sends optional trimmed tenant/workspace hints without browser storage. Campaign batch retry/requeue paths now page and claim work before retry publication. Foundation global-enterprise and performance-ledger workspace-owned reads now fail closed on missing workspace context and use exact workspace predicates. Product parity docs now separate completed local contracts from future work without new market claims. Codex audit/checkpoint hygiene now enforces uppercase audit event types and marks completed 2026-05-22 checkpoints done while preserving history.

Validation: full foundation plus campaign reactor tests passed, frontend lint/build and marketing Playwright passed, Codex lifecycle and system validation passed before queue completion, monitor check and cleanup dry-run passed, and scoped diff checks passed. Final closeout validation is recorded in the active-work handoff and audit trail.

Residual risk: production release evidence, high-volume proof, provider capacity, legacy campaign eligibility-marker policy, automation script sandbox evidence, and tracking runtime evidence remain blocked by external evidence or human decisions.

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
## 2026-05-22 Campaign Send Execution Campaign Workspace Scope

Source: `services/campaign-service/src/main/java/com/legent/campaign/service/SendExecutionService.java` and `services/campaign-service/src/test/java/com/legent/campaign/service/SendExecutionServiceTest.java`.

Outcome: campaign send execution now resolves the campaign by tenant, batch workspace, and campaign ID after the send batch is claimed under tenant/workspace scope. If a legacy or malformed batch points at a campaign outside the batch workspace, the batch fails closed before content rendering or `email.send.requested` publication.

Validation: refreshed local shared artifacts with `./mvnw.cmd -pl shared/legent-common,shared/legent-kafka -am -DskipTests install`; focused single regression passed; full `SendExecutionServiceTest` passed with 23 tests; full `./mvnw.cmd -pl services/campaign-service -DforkCount=0 test` passed with 108 tests; repo artifact hygiene passed; Codex validation passed; scoped `git diff --check` passed with CRLF warnings only.

Residual risk: this is local campaign-service validation only. It does not close the blocked legacy eligibility-marker compatibility decision, provider capacity evidence, target scheduler/load proof, or strict production release evidence.

## 2026-05-22 Parallel Safe Local Slices Integration

Source: parallel subagent slices across tracking, delivery, content/identity security tests, frontend signup/sanitization, audience subscriber-key scoping, foundation public contact hardening, route/compose/release validators, and product parity docs.

Outcome: integrated the disjoint local slices, fixed review findings, and added extra guard coverage: delivery no longer performs DNS-resolving click-destination validation during send-time rewriting; tracking enforces full public URL validation at click redirect; subscriber key uniqueness is workspace-scoped and Flyway-owned; compose safety now runs through release-gate; rendered production overlay validation checks per-container posture.

Validation: focused common/delivery/tracking/audience tests passed, campaign send execution tests passed earlier, content/identity/foundation focused tests passed, frontend lint and targeted auth/sanitize Playwright passed, route-map live plus fixture harness passed, compose safety self-test/config passed, production overlay validation passed, release evidence self-test passed, local-only release gate passed, repo artifact hygiene passed, and `git diff --check` passed with CRLF warnings only.

Residual risk: local evidence only. Strict production release, provider capacity, target migration/Flyway, ClickHouse runtime, live monitoring, and high-volume send evidence remain external blockers.

## 2026-05-22 Backend Workspace-Scope Safety Batch

Source: disjoint automation, deliverability, and content/shared Kafka worker scopes in `WorkflowScheduleTriggerJob`, sender-domain verification/challenge paths, content template workflow publication, and `EventContractValidator`.

Outcome: schedule trigger jobs now fail closed on malformed or stale tenant/workspace/schedule data before publishing workflow triggers; sender-domain challenge and verification ID lookups are tenant+workspace scoped before DNS/save/history side effects; content template workflow and published events carry workspace scope and shared contract validation.

Validation: focused automation, deliverability, and content/shared Kafka gates passed; integrated `.\mvnw.cmd -T 1 -pl services/automation-service,services/deliverability-service,services/content-service,shared/legent-kafka -am test` passed.

Residual risk: local test evidence only. Production scheduler runtime, live DNS/provider behavior, downstream Kafka consumers, strict release evidence, and high-volume evidence remain external blockers.

## 2026-05-22 Parallel Safety and Fanout Batch

Source: parallel workers for platform webhook fanout, shared runtime/Kafka guards, tracking outbox scope, delivery feedback outbox scope, and frontend core API preflight.

Outcome: platform webhook dispatch now pages active configs and dispatches one bounded page at a time; prod/production profiles reject unsafe `ddl-auto` and Kafka trusted packages cannot be widened; tracking and delivery outbox publish claim/reload paths are tenant+workspace scoped; frontend `/api/v1/core` calls now require workspace context unless explicitly optional.

Validation: focused backend gate passed for selected shared/platform/tracking/delivery tests; full `.\mvnw.cmd -T 1 -pl shared/legent-common,shared/legent-kafka,services/platform-service,services/tracking-service,services/delivery-service -am test` passed; frontend API-client and admin Playwright specs passed with project-local `playwright.cmd`; frontend lint and `npm run build:ci` passed; repo artifact hygiene and `git diff --check` passed with CRLF warnings only.

Residual risk: local evidence only. Production startup, live Kafka/PostgreSQL, webhook fanout load, provider/runtime replay, and strict release evidence remain separate blockers.

## 2026-05-22 Parallel Implementation Hardening Batch

Source: six exact-leased workers for identity invitation creation, Compose health validation, campaign dead-letter listing, campaign approval mutation, automation run/history reads, and frontend platform API preflight.

Outcome: identity invitation creation now requires principal workspace context and rejects workspace mismatches before service side effects; Compose health validation accepts explicit env-file/compose-file inputs and has self-test fixtures; campaign dead-letter listing is bounded by default/max limits; campaign approval approve/reject/cancel resolves approvals through the owning campaign workspace before mutation; automation workflow run and history reads are bounded; frontend platform notifications and webhooks now require workspace context before dispatch.

Validation: combined backend gate passed with `.\mvnw.cmd -T 1 -pl services/identity-service,services/campaign-service,services/automation-service -am test`; frontend API-client Playwright spec passed with 10 tests; frontend lint and `npm run build:ci` passed; Compose health self-test, `-AllowNotRunning -ComposeEnvFile .env.example`, and `docker compose --env-file .env.example config --quiet` passed; Codex validation and repo artifact hygiene passed; `git diff --check` passed with CRLF warnings only.

Residual risk: local evidence only. Docker running-container health, target campaign DLQ/load behavior, workflow history production volume, and strict production release evidence remain separate blockers.

## 2026-05-22 Frontend Reset URL Scrub

Source: `frontend/src/components/marketing/PublicAuthViews.tsx` and `frontend/tests/e2e/marketing.spec.ts`.

Outcome: the public reset screen now captures the reset credential once into component state, removes query parameters from the browser URL, preserves the missing-credential state, and submits the captured value without writing it to browser storage.

Validation: targeted Chromium marketing Playwright passed with 6 tests, frontend lint passed, frontend production build passed, Codex validation passed, repo artifact hygiene passed, and `git diff --check` passed with CRLF warnings only.

Residual risk: local browser/test evidence only; backend reset semantics and production runtime behavior remain separate validation surfaces.

## 2026-05-22 Outbox Backlog Observability

Source: tracking/delivery outbox services and repositories, focused outbox tests, Prometheus alerts, Grafana overview, and the production hardening runbook.

Outcome: tracking and delivery feedback outboxes now expose low-cardinality ready-depth and oldest-ready-age gauges with queue-only labels, Prometheus warning/critical alerts, Grafana overview panels, and runbook triage guidance.

Validation: focused tracking/delivery outbox tests passed, full delivery+tracking backend module gate passed with expected Testcontainers skips, production overlay validation passed with the existing external secret placeholder warning, Grafana JSON parsed, Codex validation passed, repo artifact hygiene passed, and `git diff --check` passed with CRLF warnings only.

Residual risk: alert thresholds are local defaults; live scrape behavior, alert routing, and backlog tuning require target environment evidence.

## 2026-05-22 Foundation Public Content Admin Context

Source: `PublicContentService.java` and `PublicContentServiceTest.java`.

Outcome: admin public-content list, upsert, and publish now require tenant and workspace context before repository lookup or save. Public marketing read behavior was not changed.

Validation: focused `PublicContentServiceTest` passed with 8 tests; integrated `.\mvnw.cmd -T 1 -pl services/foundation-service,services/content-service -am test` passed; scoped diff check passed with CRLF warnings only.

Residual risk: local service/module evidence only; target runtime behavior remains normal release evidence.

## 2026-05-22 Content Test-Send History Bound

Source: `TemplateTestSendService.java`, `TemplateTestSendRecordRepository.java`, and `TemplateTestSendServiceTest.java`.

Outcome: template test-send history now reads a bounded first page through the repository while preserving tenant/workspace/template/deleted-row filtering and the existing list response shape.

Validation: focused `TemplateTestSendServiceTest` passed with 3 tests; integrated foundation+content backend gate passed; scoped diff check passed with CRLF warnings only.

Residual risk: no target high-volume history evidence collected.

## 2026-05-22 CI Compose Env-File Safety Gate

Source: `.github/workflows/ci-security.yml` and `scripts/ops/test-release-evidence-validators.ps1`.

Outcome: CI Compose config smoke now uses the reviewed env-file path explicitly, and release self-tests guard against returning to ambient Compose config discovery.

Validation: release evidence validator self-test passed, Compose safety self-test passed, Compose safety validation with `.env.example` passed, `docker compose --env-file .env.example config --quiet` passed, and scoped diff check passed with CRLF warnings only.

Residual risk: local CI/render validation only; no containers were started and no production evidence was collected.

## 2026-05-22 Deliverability Suppression History Count Query

Source: `SuppressionController.java`, `SuppressionListRepository.java`, and `SuppressionControllerTest.java`.

Outcome: suppression history now computes the total with a tenant/workspace scoped count query instead of loading all suppression rows, while preserving complaint, hard-bounce, unsubscribe, and response-shape behavior.

Validation: focused `SuppressionControllerTest` passed with 9 tests; full deliverability-service reactor gate passed with 70 deliverability-service tests plus upstream shared modules; scoped diff check passed with CRLF warnings only.

Residual risk: target suppression-volume/runtime evidence remains separate from this local performance fix.

## 2026-05-22 Frontend Template Preference Scope

Source: `frontend/src/app/(workspace)/email/templates/page.tsx` and `frontend/tests/e2e/template-builder.spec.ts`.

Outcome: template library favorites and recents now use browser-storage keys scoped to the active tenant and workspace, ignore legacy global keys, clear local preference state when workspace context is missing, preserve the recent cap of 20, and treat corrupt scoped JSON as an empty list.

Validation: targeted Chromium template-builder Playwright passed with 5 tests; frontend lint passed; frontend production build passed; scoped `git diff --check` passed with CRLF warnings only.

Residual risk: local browser/test evidence only; no backend API or credential-storage behavior was changed.

## 2026-05-22 Delivery Provider Capacity List Bound

Source: `DeliveryOperationsController.java`, `ProviderCapacityService.java`, `ProviderCapacityProfileRepository.java`, and `ProviderCapacityServiceTest.java`.

Outcome: delivery provider-capacity list reads now accept a bounded optional limit, clamp invalid limits to the default and excessive limits to the max, keep tenant/workspace repository filtering, and preserve the existing `ApiResponse<List<ProviderCapacityProfile>>` shape.

Validation: focused provider-capacity/RBAC gate passed with 14 tests; full delivery-service reactor gate passed with 105 delivery-service tests plus upstream shared modules; scoped `git diff --check` passed with CRLF warnings only.

Residual risk: provider capacity, production send, and target load evidence remain external blockers.

## 2026-05-22 Audience Import Scoped Job Claims

Source: `ImportProcessingService.java`, `ImportService.java`, `ImportProcessingServiceTest.java`, and `ImportServiceTest.java`.

Outcome: async audience import processing now receives trusted tenant/workspace scope from import start paths and uses scoped job lookups for the initial load, transactional reloads, progress updates, completion, failure, and cancellation checks before storage, save, event, or context side effects.

Validation: focused import tests passed; integrated audience/deliverability/foundation backend gate passed; scoped `git diff --check` passed with CRLF warnings only.

Residual risk: target import-load and runtime storage evidence remain separate.

## 2026-05-22 Deliverability Reputation Recovery Paging

Source: `ReputationEngine.java`, `DomainReputationRepository.java`, and `ReputationEngineTest.java`.

Outcome: scheduled reputation recovery now reads domain reputation rows in deterministic fixed-size pages instead of loading all rows, while preserving missing-workspace skips and cache-failure no-save behavior.

Validation: focused reputation engine tests passed; integrated audience/deliverability/foundation backend gate passed; scoped `git diff --check` passed with CRLF warnings only.

Residual risk: target reputation-volume/runtime evidence remains separate.

## 2026-05-22 Operations Assistance Telemetry Governance

Source: `OperationsAssistanceService.java` and `OperationsAssistancePerformanceServiceTest.java`.

Outcome: operations assistance now requires workspace context, rejects restricted telemetry-key shapes before repository access, and persists only allowlisted operational telemetry fields. No external provider calls were added.

Validation: focused operations assistance and optimization tests passed; integrated audience/deliverability/foundation backend gate passed; scoped `git diff --check` passed with CRLF warnings only.

Residual risk: broader provider governance and product AI claims remain separate.

## 2026-05-22 Observability Handoff Validation

Source: `alertmanager.yml`, `prometheus-alerts.yml`, `grafana-legent-overview.json`, `validate-production-overlay.ps1`, and `production-hardening-runbook.md`.

Outcome: local production overlay validation now checks alert team ownership, Alertmanager routes/receivers, runbook anchors, Grafana dashboard UID/panel references, and receiver handoff settings before local release handoff.

Validation: production overlay validation passed, release evidence validator self-test passed, local-only release gate passed, and scoped `git diff --check` passed with CRLF warnings only.

Residual risk: target alert firing, live notification routing, Grafana live data, and monitoring handoff evidence remain external blockers.

## 2026-05-22 Data-Extension Pagination Guards

Source: `DataExtensionController.java` and `DataExtensionControllerTest.java`.

Outcome: data-extension list and record reads now clamp negative pages to the first page, invalid sizes to the default, and excessive sizes to the shared maximum while preserving tenant/workspace service scoping and response contracts.

Validation: focused controller tests passed in the worker, integrated audience+content backend reactor gate passed, and scoped diff check passed with CRLF warnings only.

Residual risk: local test evidence only; target persistence/load behavior remains separate.

## 2026-05-22 Segment Scheduled Recompute Paging

Source: `SegmentEvaluationService.java`, `SegmentRepository.java`, and `SegmentEvaluationServiceTest.java`.

Outcome: scheduled segment recompute now scans candidate segments in deterministic bounded pages and delegates each row through the existing recompute behavior with workspace context installed.

Validation: focused segment evaluation tests passed in the worker, integrated audience+content backend reactor gate passed, and scoped diff check passed with CRLF warnings only.

Residual risk: live JPA behavior and high-volume materialization evidence remain separate.

## 2026-05-22 Send Governance Pagination Guards

Source: `SendGovernancePolicyController.java` and `SendGovernancePolicyControllerTest.java`.

Outcome: send-governance policy list reads now clamp page and size params to safe defaults and maximums while preserving tenant/workspace scope, method security, internal lookup behavior, and response contracts.

Validation: focused controller tests passed in the worker, integrated audience+content backend reactor gate passed, and scoped diff check passed with CRLF warnings only.

Residual risk: local test evidence only; target operator-volume behavior remains separate.

## 2026-05-22 Tenant Bootstrap Active Paging

Source: `TenantBootstrapInitializer.java`, `TenantRepository.java`, and `TenantBootstrapInitializerTest.java`.

Outcome: startup bootstrap initialization now scans active non-deleted tenants in deterministic bounded pages instead of loading all tenants, while preserving bootstrap status skips and request payloads.

Validation: focused tenant bootstrap tests passed in the worker, integrated foundation+content+platform backend reactor gate passed, and scoped diff check passed with CRLF warnings only.

Residual risk: local test evidence only; target startup/runtime paging evidence remains separate.

## 2026-05-22 Platform Search Result Bound

Source: `GlobalSearchService.java`, `SearchIndexDocRepository.java`, and `GlobalSearchServiceTest.java`.

Outcome: platform global search now reads a bounded first page through tenant/workspace filters and returns the existing list shape to callers.

Validation: focused global search tests passed in the worker, integrated foundation+content+platform backend reactor gate passed, and scoped diff check passed with CRLF warnings only.

Residual risk: local test evidence only; target search-volume behavior remains separate.

## 2026-05-22 Unread Notification Bound

Source: `NotificationEngine.java`, `NotificationRepository.java`, and `NotificationEngineTest.java`.

Outcome: unread notification reads now use a bounded first page sorted by newest records while preserving tenant/workspace/user filters and create/mark-read behavior.

Validation: focused notification tests passed in the worker, integrated foundation+content+platform backend reactor gate passed, and scoped diff check passed with CRLF warnings only.

Residual risk: local test evidence only; target notification-volume behavior remains separate.

## 2026-05-22 Template List And Search Bounds

Source: `TemplateController.java`, `TemplateService.java`, `EmailTemplateRepository.java`, `TemplateServiceTest.java`, and `TemplateControllerTest.java`.

Outcome: template list reads now clamp page and size params, and template search now uses a bounded first page while preserving tenant/workspace scope, method security, and response contracts.

Validation: focused content tests passed in the worker, integrated foundation+content+platform backend reactor gate passed, and scoped diff check passed with CRLF warnings only.

Residual risk: local test evidence only; target template-volume behavior remains separate.

## 2026-05-22 Delivery Provider Health Bounds

Source: `ProviderHealthMonitoringService.java`, `SmtpProviderRepository.java`, `ProviderHealthCheckRepository.java`, and `ProviderHealthMonitoringServiceTest.java`.

Outcome: provider health monitoring now scans active providers in deterministic ID pages and reads recent health-check history with a bounded newest-first query while preserving workspace ownership and status updates.

Validation: focused provider health tests passed, integrated delivery/campaign/deliverability/tracking/foundation backend reactor gate passed, and scoped diff check passed with CRLF warnings only.

Residual risk: runtime provider evidence and multi-replica scheduler behavior remain separate.

## 2026-05-22 Campaign Experiment Evaluation Bounds

Source: `CampaignEngineService.java`, `CampaignExperimentRepository.java`, and `CampaignEngineServiceTest.java`.

Outcome: scheduled campaign experiment winner evaluation now scans active experiments through bounded deterministic pages while preserving winner promotion behavior and campaign readiness checks.

Validation: focused campaign engine tests passed in the worker, integrated delivery/campaign/deliverability/tracking/foundation backend reactor gate passed, and scoped diff check passed with CRLF warnings only.

Residual risk: runtime scheduler and campaign-volume evidence remain separate.

## 2026-05-22 Suppression List Bounds

Source: `SuppressionController.java`, `SuppressionListRepository.java`, `SuppressionControllerTest.java`, and `DomainControllerRbacTest.java`.

Outcome: suppression list reads now use bounded tenant/workspace scoped pages for public and internal list paths, and RBAC reflection coverage matches the bounded internal endpoint signature.

Validation: focused suppression/RBAC tests passed, integrated delivery/campaign/deliverability/tracking/foundation backend reactor gate passed, and scoped diff check passed with CRLF warnings only.

Residual risk: local test evidence only; target suppression-volume behavior remains separate.

## 2026-05-22 Tracking Campaign Summary Bounds

Source: `AnalyticsController.java`, `CampaignSummaryRepository.java`, and `AnalyticsControllerTest.java`.

Outcome: tracking campaign summary list reads now use bounded tenant/workspace scoped first-page access while preserving the existing list response shape.

Validation: focused analytics controller tests passed in the worker, integrated delivery/campaign/deliverability/tracking/foundation backend reactor gate passed, and scoped diff check passed with CRLF warnings only.

Residual risk: target analytics-volume evidence remains separate.

## 2026-05-22 Delivery Warmup And Rate-State Bounds

Source: `DeliveryOperationsController.java`, `WarmupService.java`, `SendRateControlService.java`, warmup/rate repositories, and delivery tests.

Outcome: delivery warmup and rate-state operator endpoints now accept safe limit params and read bounded tenant/workspace scoped first pages with default/max clamps.

Validation: focused warmup/rate tests passed in the worker, integrated delivery/campaign/deliverability/tracking/foundation backend reactor gate passed, and scoped diff check passed with CRLF warnings only.

Residual risk: provider/runtime queue evidence remains separate.

## 2026-05-22 Foundation Admin Settings Bounded Query

Source: `AdminSettingsService.java`, `ConfigRepository.java`, and `AdminSettingsServiceTest.java`.

Outcome: admin settings listing now reads scoped visible settings through bounded pages instead of scanning all config rows in memory.

Validation: focused admin settings tests passed in the worker, integrated delivery/campaign/deliverability/tracking/foundation backend reactor gate passed, and scoped diff check passed with CRLF warnings only.

Residual risk: focused service coverage validates repository invocation; target persistence-volume evidence remains separate.

## 2026-05-22 Workspace, RBAC, Sanitizer, Automation, Webhook, And Compose Guardrails

Source: `SendExecutionService.java`, `IngestionController.java`, `EmailContentValidationService.java`, automation workflow/schedule/activity services and repositories, `WebhookController.java`, `WebhookConfigRepository.java`, `validate-compose-safety.ps1`, and focused tests.

Outcome: campaign batch execution now fails closed without workspace context; tracking conversion ingestion requires write permission; backend content sanitization adds safe rel values for blank-target anchors; automation workflow/schedule/activity lists use bounded first pages; webhook admin listing uses bounded scoped first-page access; Compose safety validation rejects high-risk runtime settings.

Validation: six worker focused gates passed, integrated backend reactor gate passed for campaign/tracking/content/automation/platform plus shared modules, Compose safety self-test passed, release evidence validator self-test passed, repo artifact hygiene passed, Codex validation passed after metadata repair, and scoped diff check passed with CRLF warnings only.

Residual risk: no production/runtime evidence was collected; list-shaped APIs still do not expose pagination metadata; strict release and high-volume evidence remain blocked externally.

## 2026-05-22 Frontend Context, DMARC, Feature Flags, Notifications, Approvals, And Route Fixtures

Source: `context-bootstrap.ts`, `DmarcController.java`, `DmarcReportRepository.java`, `FeatureFlagController.java`, `NotificationController.java`, `TemplateWorkflowController.java`, `TemplateWorkflowService.java`, `TemplateApprovalRepository.java`, route-map validator scripts, and focused tests.

Outcome: frontend context bootstrap now honors preferred environment selection; DMARC report history is bounded; feature flag admin list/create requires tenant context; platform notification read/mark-read declares explicit method authentication; template approval history is bounded; route-map negative fixtures now cover tracking route and ingress policy drift.

Validation: worker focused gates passed, integrated backend reactor gate for deliverability/foundation/platform/content plus shared modules passed, frontend lint/build/context Playwright passed, route fixture/live validation passed, repo artifact hygiene passed, Codex validation passed, and scoped diff check passed with CRLF warnings only.

Residual risk: credentialed E2E login remained skipped without configured test credentials; list-shaped APIs still lack pagination metadata; evidence remains local, not production promotion proof.

## 2026-05-22 Auth Cleanup, Analytics Windows, Version Bounds, Identity Bounds, Config Context, And CI Validators

Source: `PublicAuthViews.tsx`, `auth.spec.ts`, tracking analytics controller/service/tests, content template-version controller/service/repository/tests, identity user/invitation controllers/services/tests, foundation config-version controller/tests, CI workflow, route-map harness, and Compose safety validator.

Outcome: login and signup now clear local auth/context state after workspace bootstrap failure; tracking analytics timeline and rollups apply bounded windows and bucket caps; content template version listing and publish cleanup are bounded; identity user and invitation list reads are bounded with workspace checks preserved; config version endpoints require tenant context before service access; CI now runs route-map fixture and Compose safety checks.

Validation: worker focused gates passed; integrated backend reactor gate passed for identity, foundation, tracking, content, and shared modules; frontend lint/build/auth Playwright passed; route-map fixture/live validation and Compose safety validation passed; scoped diff check passed with CRLF warnings only.

Residual risk: local evidence only; no GitHub Actions runner, live database integration, ClickHouse/runtime, or production evidence was collected.

## 2026-05-22 Compliance, WebSocket, Reputation, Content Block, Frontend API, And DevOps Validator Hardening

Source: `ComplianceEvidenceService.java`, `TenantHandshakeInterceptor.java`, `PredictiveDeliverabilityService.java`, `DeliverabilityInsightsController.java`, `ContentBlockService.java`, `api-client.ts`, release/Compose validator scripts, CI workflow, and focused tests.

Outcome: compliance evidence now requires trusted workspace context and exact workspace predicates; tracking WebSocket handshakes no longer fall back to request headers for scope; deliverability reputation and insight reads use bounded windows; content block version lists and publish cleanup are bounded; frontend public API helpers reject external absolute targets before dispatch; release validation utilities reuse the active PowerShell executable and CI covers Compose health parser self-tests.

Validation: focused worker gates passed; integrated backend reactor gate passed for foundation, tracking, deliverability, content, and shared modules; frontend lint/build/API-client Playwright passed; release evidence validators, local release gate, Compose health/safety validators, route-map validators, repo artifact hygiene, Codex validation, and scoped diff check passed.

Residual risk: evidence is local only. Insight averages now use a bounded recent window, and production release, target runtime, load, provider, monitoring, and external evidence remain blocked until collected.

## 2026-05-23 Identity SCIM List Bounds

Source: `ScimProvisioningService.java`, `UserRepository.java`, and `ScimProvisioningServiceTest.java`.

Outcome: SCIM user listing no longer loads all tenant users before provider filtering. It queries active users by tenant plus SCIM provider with bounded offset/page size, and `userName eq` filtering uses a tenant/provider/active lookup.

Validation: focused `.\mvnw.cmd -pl services/identity-service -am "-Dtest=ScimProvisioningServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with 9 tests.

Residual risk: local unit/service evidence only; target SCIM IdP paging behavior and production auth telemetry remain release evidence concerns.

## 2026-05-23 Delivery Send Request Tenant Guard

Source: `DeliveryEventConsumer.java` and `DeliveryEventConsumerTest.java`.

Outcome: delivery send-request events now fail closed when tenant scope is missing before workspace resolution, idempotency claims, `TenantContext` setup, or orchestration side effects.

Validation: focused `.\mvnw.cmd -pl services/delivery-service -am "-Dtest=DeliveryEventConsumerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with 5 tests. A first non-reactor attempt failed because PowerShell parsing/upstream shared compilation required quoted properties and `-am`; rerun passed.

Residual risk: local consumer coverage only; target Kafka replay, provider behavior, and production evidence remain separate blockers.

## 2026-05-23 Campaign Kafka Tenant Guard

Source: `CampaignEventConsumer.java` and `CampaignEventConsumerTest.java`.

Outcome: campaign event registration now requires nonblank tenant scope before idempotency registration, which protects claimed audience, send-processing, batch-created, automation-send, tracking-ingested, and delivery-feedback reconciliation paths.

Validation: focused `.\mvnw.cmd -pl services/campaign-service "-Dtest=CampaignEventConsumerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with 25 tests.

Residual risk: local consumer coverage only; target Kafka replay, high-volume processing, and production release evidence remain separate blockers.
