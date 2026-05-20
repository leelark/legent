# Successful Fixes

Fresh baseline date: 2026-05-20.

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

No product fix entries exist in the fresh memory baseline.

Current non-product success:
- 2026-05-20: `.codex` autonomous organization was rebuilt and hardened as a project operating system. Evidence: changed files under `.codex/`, root operating docs, `docs/operations/`, `docs/audits/`, `docs/product/`, and `scripts/ops/`. Validation included Codex system validation, route map, env example, production overlay, repo artifact hygiene, egress evidence template, release evidence validator self-test, local release gate, Compose config with `.env.example`, Kustomize render, JSON parsing, PowerShell parser checks, and `git diff --check`.
- 2026-05-20: Production egress evidence validation now rejects placeholder evidence. Source problem: the checked-in egress evidence template passed validation with `example-*` fields and RFC documentation CIDR values. Changed files: `validate-production-egress-evidence.ps1`, `test-release-evidence-validators.ps1`, `ga-evidence-matrix.md`, release command docs, validation gates, and dated report errata. Validation: release evidence self-test passed; direct template validation failed as expected; local release gate, Codex validation, and `git diff --check` passed. Residual risk: real production egress evidence and generated policy inclusion proof remain separate requirements.
- 2026-05-20: Strict production egress validation now proves reviewed policy render inclusion. Source problem: reviewed egress evidence could be validated without proving the generated reviewed external egress NetworkPolicy rendered into the production manifest, and stale generated policy artifacts were not hash-tied to the evidence. Changed files: `write-production-egress-policy.ps1`, `validate-production-egress-policy-render.ps1`, `validate-production-egress-evidence.ps1`, `release-gate.ps1`, and `test-release-evidence-validators.ps1`. Validation: release evidence self-test, production overlay validation, production Kustomize render, local release gate, Codex validation, repo artifact hygiene, and `git diff --check`. Residual risk: target-environment egress, image, GA, load, restore, CI/security, TLS/admission, and monitoring evidence still block production promotion.

Current product success:
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

Future entries must include:
- source problem,
- changed files,
- validation commands,
- residual risk,
- release note impact.
