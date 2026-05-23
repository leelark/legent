# Root Cause History

Fresh baseline date: 2026-05-20.

## 2026-05-22 Safe Local Pool Q Closeout

Root causes:
- Public account recovery had backend-supported tenant/workspace hint fields available, but the public UI did not collect or submit them, making account recovery less precise for multi-tenant users.
- Campaign retry/requeue paths scanned job batches too broadly and could publish retry events before proving the batch row was claimed by the current worker.
- Foundation global-enterprise and performance-ledger helpers allowed nullable workspace predicates to behave like workspace wildcards on workspace-owned surfaces.
- Product parity docs mixed completed local contracts with broad future parity gaps, making backlog selection less precise.
- Codex audit/checkpoint state allowed lowercase audit event type drift and left completed 2026-05-22 checkpoints marked in progress.

Fix: added optional trimmed account-recovery hints, switched campaign retry/requeue to bounded claim-before-publish behavior, required trusted workspace context and exact workspace predicates in the foundation helpers, split product docs into completed-local versus future/evidence-bound work, and hardened Codex audit/checkpoint validation.

Validation: focused worker validations passed, the full foundation plus campaign reactor passed, frontend lint/build and marketing Playwright passed, and Codex lifecycle/system validation plus cleanup/monitor checks passed during the pool integration.

Avoidance: public tenant-aware flows should submit backend-supported disambiguation safely, scheduler/retry workers must claim bounded scoped work before events, workspace-owned foundation helpers must not use nullable wildcard predicates, parity docs should separate local evidence from future claims, and Codex validation should guard casing/status drift.

## 2026-05-22 Route Map Ingress Prefix Coverage

Root cause: `validate-route-map.ps1` checked local Nginx prefix-to-upstream ownership, but Kubernetes ingress validation only warned when a route-map service name was absent from ingress text. Regex-grouped ingress paths could therefore lose or misroute one prefix while validation still passed if the service name appeared elsewhere.

Fix: added Kubernetes ingress path/backend extraction and first-match coverage checks for every route-map prefix. The validator now proves each prefix is covered by an ingress path that routes to the expected service and catches missing or misordered specific routes before broad aliases.

Validation: route-map validation passed; a temporary missing-prefix ingress fixture failed on `/api/v1/templates`; production Kustomize render, production overlay validation, repository artifact hygiene, Codex validation, and scoped diff checks passed.

Avoidance: route-map, Nginx, and ingress changes must keep prefix/backend ownership proof in the route validator rather than relying on broad service-name presence.

## 2026-05-22 Workspace Context Fail-Closed E2E

Root cause: `WorkspaceLayout` had fail-closed logic for sessions that lack workspace context, but `context-bootstrap.spec.ts` only covered the credential-gated happy path. Without a mocked missing-context browser test, future layout or bootstrap changes could render workspace routes with stale local context after a session response omitted workspace ownership.

Fix: added a deterministic Playwright test that seeds stale local context, mocks a successful session without `workspaceId`, returns no account contexts, visits `/app/email`, and asserts redirect to login plus local user/role/tenant/workspace/environment cleanup.

Validation: frontend lint, frontend production build, targeted Chromium `context-bootstrap.spec.ts`, repository artifact hygiene, Codex validation, and scoped diff check passed.

Avoidance: auth/session hydration and workspace layout changes must keep both happy-path and missing-workspace browser cases in E2E coverage.

## 2026-05-22 Campaign Scheduled Publish Workspace Context

Root cause: the scheduled campaign worker loaded and claimed due send jobs with tenant/workspace row ownership, but invoked `CampaignEventPublisher.publishAudienceResolutionRequested` from a scheduler thread without setting `TenantContext`. The publisher builds workspace-scoped Kafka envelopes from thread-local context, so scheduled sends could fail at publish time or lose workspace envelope ownership.

Fix: require tenant/workspace values from the claimed job row, use them for claim and campaign lookup, wrap the audience-resolution publish call in a temporary tenant/workspace context, and restore the previous thread context in a `finally` block. Publisher tests now prove audience-resolution envelopes and payloads carry workspace ownership.

Validation: focused `SchedulingServiceTest,CampaignEventPublisherTest` passed with 7 tests; full campaign-service gate passed with 107 campaign-service tests plus upstream modules; repo artifact hygiene, Codex validation, and scoped diff checks passed.

Avoidance: scheduled or recovery workers that publish through context-dependent producers must install trusted scope from the claimed row before publication and clear or restore context afterward.

## 2026-05-22 Release Gate Strict Skip Guard

Root cause: `release-gate.ps1` required strict evidence flags for non-local promotion mode, but it still honored local gate skip flags after that check. A strict invocation could therefore skip backend, frontend, Compose, or Kustomize validation while emitting strict-mode completion text.

Fix: added an early strict-mode skip-flag guard that rejects `-SkipBackend`, `-SkipFrontend`, `-SkipCompose`, and `-SkipKustomize` unless `-LocalOnly` is present; added a release evidence self-test fixture that proves strict mode fails before evidence validation when skip flags are supplied.

Validation: release evidence validator self-test passed with the new negative fixture; local-only release gate with all skip flags still passed; repo artifact hygiene and scoped diff checks passed.

Avoidance: release gates must keep non-promotional local convenience flags separate from strict promotion semantics and include negative fixtures for unsafe flag combinations.

## 2026-05-21 Deliverability Controller RBAC Coverage

Root cause: deliverability domain management and insights controllers had explicit method-level permissions, but suppression list/history, DMARC reports/ingest, and reputation reads only required authentication plus tenant/workspace context. That left role-level authorization inconsistent across deliverability operator surfaces.

Fix: added `deliverability:read` checks to suppression list/history, DMARC reports, and reputation score reads; added `deliverability:write` to DMARC ingest; preserved the existing internal suppression service guard; and expanded reflection tests to fail if authenticated deliverability controller endpoints lack RBAC.

Validation: focused `DomainControllerRbacTest,SuppressionControllerTest` passed with 17 tests; full deliverability-service gate passed with 50 tests; artifact hygiene, Codex validation, and scoped diff check passed.

Avoidance: every authenticated deliverability controller endpoint should declare method-level read/write permission unless it is an explicitly documented internal service endpoint with its own credential guard.

## 2026-05-21 Campaign Scheduler Bounded Claims

Root cause: scheduled campaign jobs and partial batch retries were selected with unbounded global status scans, then transitioned by mutating loaded entities. In a multi-replica scheduler, two nodes could load the same eligible rows and publish duplicate audience-resolution or batch-retry work before either transition became visible.

Fix: added bounded pageable due-job, stale-processing, and partial-batch queries; added tenant/workspace/id/status compare-and-claim update methods for due jobs, stale processing recovery, partial retry requeue, and exhausted partial failure; updated scheduler services to skip rows when claims lose a race; and added focused claim-skip tests.

Validation: focused `SchedulingServiceTest,SendExecutionServiceTest` passed with 24 tests; full campaign-service test gate passed with 95 tests; Codex validation and scoped diff check passed.

Avoidance: scheduled workers that publish downstream events must claim bounded work by exact tenant/workspace/id/status before publication and test claim-loss behavior.

## 2026-05-21 Content Send Governance Internal Security Chain

Root cause: the email governance policy object slice added an internal `GET /api/v1/content/send-governance-policies/{id}/internal` controller endpoint and campaign-service client, but content-service `SecurityConfig` only allowlisted the existing render and rendered-content internal routes. Public edge denies existed, and the controller checked `X-Internal-Token`, but unauthenticated service-to-service calls could still be stopped by the service filter chain before reaching that guard.

Fix: added the send-governance internal GET route to the content-service security allowlist; added a security-chain test proving the route reaches a controller without JWT; added a controller test proving invalid internal tokens fail closed before service access.

Validation: focused `SecurityConfigTest`, `SendGovernancePolicyControllerTest`, and `ContentControllerRbacTest` passed with 12 tests; route-map validation, Codex validation, and scoped diff check passed.

Avoidance: every new service-internal route needs both public-edge deny validation and service-local security-chain coverage for the intended internal authentication guard.

## 2026-05-20 Foundation Config Version History Scope

Root cause: `system_configs` became tenant/workspace/environment scoped in later migrations, but `config_version_history` and versioning service APIs remained tenant/key/version only. History reads, compare, rollback, and version allocation could therefore cross workspace/environment boundaries.

Fix: added nullable workspace/environment history columns and exact-scope repository methods; updated config writes, version reads, compare, admin/controller callers, and rollback to use exact nullable scope; made history writes authoritative; added rollback cache/event side effects; and added exact-scope unique version indexing.

Avoidance: whenever a live entity gains tenant/workspace/environment scope, its audit/history/version tables and service APIs must gain the same identity before rollback or compare features are considered safe.

## 2026-05-21 Tracking Ingestion Batch Retry Horizon

Root cause: the first batch-consumer slice treated `IN_PROGRESS` idempotency claims as retryable, but the tracking batch listener inherited the shared Kafka error handler whose roughly 30-second DLQ horizon was shorter than the default 15-minute stale-claim reclaim window. A crash-after-claim event could therefore be recovered to DLQ before it became reclaimable.

Fix: added a tracking-only batch listener factory error handler with a retry horizon tied to the stale-claim age plus a configured buffer, kept the shared Kafka handler unchanged, set tracking-only `max.poll.interval.ms`, and added fail-fast config tests.

Avoidance: any Kafka consumer that uses durable stale-claim recovery must align retry/DLQ timing and max poll interval with the claim reclaim window instead of reusing a short shared handler blindly.

## 2026-05-21 Tracking Idempotency Migration Test Gating

Root cause: the V13 raw-write phase migration assertion lived inside a class-level Testcontainers test, so no-Docker validation skipped both PostgreSQL behavior and Docker-independent migration drift checks.

Fix: split the V13 migration assertion into a no-Spring, no-Testcontainers test while keeping PostgreSQL/Flyway behavior in the Docker-gated idempotency service test.

Avoidance: static migration contract tests should not be colocated with Docker-gated integration classes unless skipping them is intentional and separately documented.

## 2026-05-20 Automation Send Handoff Review Hardening

Root cause: the first governed send handoff slice mixed pre-publish idempotency registration with Kafka publication, exact-key unsafe override checks, and campaign-wide send semantics that were not explicit enough when a workflow subscriber trace was present.

Fix: `SendEmailNodeHandler` now uses claim/publish/mark/release behavior; Automation Studio and workflow graph checks normalize unsafe send override keys; shared Kafka validation enforces true launch confirmation and matching payload/envelope idempotency; campaign consumption requires campaign-orchestration markers when a subscriber trace is present and tests prove campaign audiences remain the send source.

Avoidance: side-effecting producers should not mark idempotency processed before the side effect is accepted. Config-key deny lists should normalize common client variants. Contact/journey traces must be explicitly separated from campaign-wide launch ownership in tests and consumer validation.

## 2026-05-20 Suppression Delete Tenant Scope

Root cause: suppression deletion was the only CRUD path using a raw ID lookup instead of the audience service's tenant/workspace scope convention.

Fix: added `findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull` to `SuppressionRepository` and switched `SuppressionService.delete` to the scoped lookup.

Avoidance: when adding delete/update paths for tenant-owned records, prefer repository methods that include tenant ID, workspace ID, ID, and `deletedAt IS NULL`; add cross-workspace denial tests.

## 2026-05-20 Segment Rules Fail Closed

Root cause: segment condition building mapped the field before handling relationship-style operators. `list_membership` intentionally mapped to `null`, so list membership clauses were skipped before the `IN_LIST` and `NOT_IN_LIST` branches could run. The default operator branch also returned `null`, which let unsupported operators disappear from the generated SQL.

Fix: `SegmentEvaluationService` now handles `IN_LIST` and `NOT_IN_LIST` before scalar field mapping, requires them to use `list_membership`, and throws on unsupported operators. `SegmentService` now validates rule trees on create/update so invalid operators and list-membership combinations cannot be persisted.

Avoidance: relationship and special-purpose segment operators must be validated before scalar field mapping, and unknown rule constructs should fail closed with regression tests instead of returning `null` clauses.

## 2026-05-20 Automation Studio Live Run Confirmation

Root cause: `AutomationStudioDto.RunRequest.dryRun` used primitive `boolean`, so Jackson and Lombok represented an omitted JSON field as `false`. The service treated `false` as live-run intent and only required the activity to be ACTIVE.

Fix: changed `dryRun` to nullable `Boolean` with fail-safe `isDryRun()` semantics, added nullable `confirmLiveRun`, and required `confirmLiveRun=true` before any non-dry run can proceed.

Avoidance: command/request DTOs for destructive or side-effecting actions should use nullable intent fields plus explicit confirmation, with `{}` regression tests.

## 2026-05-20 Platform Event Idempotency

Root cause: `PlatformEventConsumer` validated platform event identity but had no durable tenant/workspace-scoped claim before webhook dispatch, notification creation, or search indexing. Kafka retries and replays could therefore repeat side effects for the same `eventId` or `idempotencyKey`.

Fix: added `platform_event_idempotency` storage, `PlatformEventIdempotencyService`, and consumer claim/mark/release flow for webhook, notification, and search topics. Duplicate claims return before side effects; side-effect failures release pending claims; processed-marker failures after a completed side effect do not release the claim.

Avoidance: Kafka consumers that perform non-idempotent side effects must claim a durable event identity before side effects and test duplicate skip, side-effect retry release, and post-side-effect mark failure behavior.

## 2026-05-20 Audience Resolution Final Eligibility First Slice

Root cause: the campaign send path delegates audience selection to audience resolution, but `AudienceResolutionConsumer` only used deliverability-service suppression checks plus a local `isSendEligible` shortcut. That shortcut was not tenant/workspace suppression-aware and did not understand preference-center nested channel or pause fields.

Fix: replaced the shortcut in resolution with `SendEligibilityService.evaluateAll`, added bulk local suppression lookup, enforced nested `channels.email=false` and future `pausedUntil`, and added regression tests for local eligibility denial and failure propagation.

Avoidance: final campaign recipient lists should carry proof that they came through audience resolution eligibility. The broader parent item remains in review until campaign legacy send-batch payloads either require an eligibility marker or are otherwise fail-closed.

Product root-cause entries:

- 2026-05-20: Feature flag by-ID tenant isolation gap.
  - Symptom: `GET /api/v1/feature-flags/{id}` lacked `@PreAuthorize`; get/update/delete used raw `FeatureFlagRepository.findById`.
  - Source evidence: `FeatureFlagController.java`, `FeatureFlagService.java`, security audit lane.
  - Causal chain: list/create paths passed tenant context, but by-ID methods did not carry tenant scope into repository lookup.
  - Fix: added controller read authorization and repository method `findByIdAndTenantIdAndDeletedAtIsNull`; service now requires `TenantContext` and uses tenant-scoped lookup for get/update/delete.
  - Validation: `.\mvnw.cmd -pl services/foundation-service -am test` passed.
  - Prevention: add tenant-scoped by-ID tests whenever protected tenant-owned resources expose direct IDs.
- 2026-05-20: Public edge exposed service-internal endpoints.
  - Symptom: internal content, audience, and deliverability endpoints used `permitAll` plus `X-Internal-Token`, but public Nginx and ingress broad prefixes still routed them.
  - Source evidence: `config/nginx/nginx.conf`, `infrastructure/kubernetes/ingress/ingress.yml`, service `SecurityConfig.java` files.
  - Causal chain: route ownership validation checked service prefix drift, but did not assert negative public-edge rules for service-to-service internal paths.
  - Fix: added public Nginx and Kubernetes ingress 404 deny rules for known internal routes, and extended `validate-route-map.ps1` to enforce the denies.
  - Validation: `scripts\ops\validate-route-map.ps1`, `kubectl kustomize infrastructure\kubernetes\overlays\production`, and `docker compose config --quiet` passed.
  - Prevention: add validator entries with any new `/internal` endpoint.
- 2026-05-20: Audience suppression lookup fetched broad workspace data.
  - Symptom: audience resolution passed candidate emails to `DeliverabilityServiceClient`, but the client fetched all deliverability suppressions for the tenant/workspace and filtered locally.
  - Source evidence: `services/audience-service/src/main/java/com/legent/audience/client/DeliverabilityServiceClient.java`, `AudienceResolutionConsumer.java`, `services/deliverability-service/src/main/java/com/legent/deliverability/controller/SuppressionController.java`.
  - Causal chain: the deliverability internal API exposed only a broad workspace suppression list, so the audience client could not perform a candidate-scoped server-side lookup.
  - Fix: added `POST /api/v1/deliverability/suppressions/internal/check`, tenant/workspace-scoped normalized repository lookup, a functional Flyway index, public-edge child-prefix denies, and normalized audience-side candidate filtering.
  - Validation: `.\mvnw.cmd -pl services/audience-service,services/deliverability-service -am test`, route validation, Kustomize render, Compose config, repo artifact hygiene, production overlay validation, Codex validation, and `git diff --check` passed.
  - Prevention: high-volume cross-service checks should expose bounded candidate APIs instead of broad list fetches, with validator-backed internal route denies.
- 2026-05-20: Delivery feedback publication was not durable.
  - Symptom: delivery state could commit as `SENT`, `FAILED`, or retryable while `email.sent`, `email.failed`, `email.bounced`, or `email.retry.scheduled` Kafka feedback was only attempted through an unobserved async future.
  - Source evidence: `DeliveryOrchestrationService.java`, `DeliveryEventPublisher.java`, shared `EventPublisher.java`, and `.codex/reports/production-readiness-audit-2026-05-20.md`.
  - Causal chain: message state updates and feedback publication were coupled in the service method, but Kafka publish futures were not awaited or stored; if made synchronous without an outbox, provider success could also be misclassified as delivery failure.
  - Fix: added `delivery_feedback_outbox_events`, stable feedback envelopes/partition keys, a leased retrying outbox publisher, after-commit publish attempts, and delivery scheduling.
  - Validation: focused delivery tests, `.\mvnw.cmd -pl services/delivery-service -am test`, Codex validation, repo artifact hygiene, and `git diff --check` passed.
  - Prevention: outbound feedback that drives cross-service reconciliation must be persisted before publish and retried with stable event IDs/idempotency keys.
- 2026-05-20: Campaign send handoff could violate the send-request contract.
  - Symptom: default campaign send settings could publish `email.send.requested` with inline rendered content but without `contentReference`, while shared Kafka validation and delivery content resolution require the reference.
  - Source evidence: `SendExecutionService.java`, `services/campaign-service/src/main/resources/application.yml`, `EventContractValidator.java`, and `DeliveryOrchestrationService.java`.
  - Causal chain: `content-reference-enabled` was treated as an optional feature flag even though the downstream contract had already made content references mandatory for delivery resolution, retry reconstruction, and high-volume payload safety.
  - Fix: campaign send now creates a rendered content reference before publish regardless of the legacy flag, defaults the flag true/deprecated, and keeps inline content only as optional fallback under the payload cap. Shared contract tests now reject inline-only and blank-reference payloads.
  - Validation: focused campaign tests, focused shared Kafka tests, `.\mvnw.cmd -pl services/campaign-service,shared/legent-kafka -am test`, Codex validation, repo artifact hygiene, and `git diff --check` passed.
  - Prevention: shared Kafka contract tests must cover both allowed reference-only payloads and rejected inline-only payloads whenever campaign or delivery handoff changes.
- 2026-05-20: Production egress evidence validator accepted placeholder evidence.
  - Symptom: `docs/operations/production-egress-evidence.template.json` passed validation even though it contained `example-*` reviewer/provider values and RFC documentation CIDR `203.0.113.10/32`.
  - Source evidence: `validate-production-egress-evidence.ps1`, `test-release-evidence-validators.ps1`, and `.codex/reports/production-readiness-audit-2026-05-20.md`.
  - Causal chain: the validator checked only a narrow placeholder set and exact broad `/0` CIDRs, so templates, documentation ranges, non-canonical CIDRs, future dates, and unsupported FQDN evidence could satisfy local release evidence checks.
  - Fix: added concrete-value checks, schema/version/date checks, template filename rejection, documentation/reserved/broad/non-canonical CIDR checks, FQDN rejection until a supported generator exists, and self-test coverage for positive and negative cases.
  - Validation: `scripts\ops\test-release-evidence-validators.ps1` passed; direct template validation failed as expected; local release gate, Codex validation, and `git diff --check` passed.
  - Prevention: release validator self-tests must include negative fixtures for checked-in templates and documentation placeholders, and strict production release remains blocked without real target evidence.
- 2026-05-20: Reviewed egress evidence was not tied to rendered NetworkPolicy output.
  - Symptom: strict release validation could validate reviewed external egress evidence without proving the generated `reviewed-external-egress` NetworkPolicy rendered through the production Kustomize overlay or that an existing generated policy matched the current evidence.
  - Source evidence: `scripts/ops/release-gate.ps1`, `scripts/ops/write-production-egress-policy.ps1`, `scripts/ops/test-release-evidence-validators.ps1`, and `infrastructure/kubernetes/overlays/production/network-policy.yml`.
  - Causal chain: evidence validation, policy generation, and Kustomize render proof were separate operations; the generated policy did not carry an evidence hash and the normal production overlay intentionally did not include generated external evidence artifacts.
  - Fix: added evidence hash/review annotations to the generated policy, added `validate-production-egress-policy-render.ps1` to render a temporary production overlay with the reviewed policy, wired strict release egress mode to that proof, rejected FQDN rules during generation, and added self-tests for valid render, missing generated policy, stale generated policy, and selector coverage.
  - Validation: `scripts\ops\test-release-evidence-validators.ps1`, production overlay validation, production Kustomize render, local release gate, Codex validation, repo artifact hygiene, and `git diff --check`.
  - Prevention: strict release evidence validators must prove both artifact freshness and rendered manifest inclusion while keeping local-only checks distinct from target-environment evidence.
- 2026-05-20: Shared Kafka DLQ was a fixed hot partition.
  - Symptom: listener failures handled by shared Kafka error handling always routed to `kafka.dead-letter` partition 0, and local Compose initialized that topic with one partition.
  - Source evidence: `KafkaConsumerConfig.java`, `KafkaTopicConfig.java`, `docker-compose.yml`, and `.codex/memory/performance-bottlenecks.md`.
  - Causal chain: the shared `DeadLetterPublishingRecoverer` used a constant `TopicPartition(AppConstants.TOPIC_KAFKA_DLQ, 0)`, while topic definitions did not enforce DLQ partition count compatibility with high-volume source topics.
  - Fix: route DLQ records to the failed record's source partition, define six DLQ partitions in Java and Compose, and test partition preservation plus source-topic partition compatibility.
  - Validation: focused Kafka config tests, `.\mvnw.cmd -pl shared/legent-kafka -am test`, Compose config, fixed-DLQ drift scan, Codex validation, repo artifact hygiene, and `git diff --check` passed.
  - Prevention: keep DLQ partition count at least as large as source topics and require production Kafka topology evidence before throughput claims.
- 2026-05-20: Journey Builder exposed nodes beyond live runtime support.
  - Symptom: the frontend Journey Builder exposed broad journey node types, while backend live runtime support was limited to `ENTRY_TRIGGER`, `SEND_EMAIL`, `DELAY`, `CONDITION`, and `END`; unsupported nodes could be silently advanced by the generic handler or activated through active-version, rollback, resume, or malformed validation paths.
  - Source evidence: `JourneyBuilder.tsx`, `NodeEditorModal.tsx`, `WorkflowGraphValidator.java`, `WorkflowStudioService.java`, `WorkflowEngine.java`, and subagent read-only audits.
  - Causal chain: graph schema support, UI design nodes, publish validation, simulation, and runtime handlers were treated as one loose surface instead of separate draft-versus-live contracts.
  - Fix: added an explicit entry-trigger handler, made wildcard runtime handling fail closed, added runtime validation before engine start/resume side effects, blocked published-definition mutation, validated active rollback/resume/save paths, exposed runtime support in capabilities, consumed capabilities in the frontend, marked unsupported nodes draft-only, and made activation require affirmative validation before published save.
  - Validation: focused automation service tests, `.\mvnw.cmd -pl services/automation-service -am test`, frontend lint, frontend production build, targeted Playwright builder tests, Codex validation, and `git diff --check` passed.
  - Prevention: keep draft node schema support separate from live runtime support, require affirmative validation before publish/activate, and add focused tests whenever a new journey node type becomes executable.
- 2026-05-20: Kubernetes tracking ingress did not express the reviewed local tracking rate policy.
  - Symptom: local Nginx used a dedicated `/api/v1/tracking` limit of `200r/s` with `burst=50 nodelay`, while Kubernetes grouped tracking ingestion with analytics and websocket paths and used non-community `rate-limit` annotations.
  - Source evidence: `config/nginx/nginx.conf`, `infrastructure/kubernetes/ingress/ingress.yml`, `scripts/ops/validate-route-map.ps1`, official community ingress-nginx annotation docs.
  - Causal chain: route ownership validation checked local Nginx route maps and internal denies, but did not prove path-specific Kubernetes tracking rate posture or controller-specific annotation keys.
  - Fix: split Kubernetes tracking ingestion into its own ingress using `nginx.ingress.kubernetes.io/limit-rps: "200"`, kept analytics/websocket routes on a separate normal `limit-rps: "100"` ingress, and extended route validation for Nginx/Kubernetes drift and singular `/api/v1/track` tombstone boundaries.
  - Validation: route validation, production and global Kustomize renders, production overlay validation, Codex validation, and `git diff --check` passed.
  - Prevention: route validation must assert path-specific ingress behavior for high-volume public routes, and production release remains blocked until target-environment rate-limit and downstream ingestion behavior are measured.
- 2026-05-20: SSO tenant cookie did not match normal auth cookie policy.
  - Symptom: normal login set `legent_tenant_id` as HTTP-only, but SSO callback cookie creation left the same tenant cookie readable by browser JavaScript.
  - Source evidence: `AuthController.java`, `SsoController.java`, read-only identity/security/test subagent audits.
  - Causal chain: SSO duplicated auth cookie creation instead of reusing a shared helper, and the tenant cookie posture diverged while access and refresh cookies remained HTTP-only.
  - Fix: set the SSO tenant cookie `HttpOnly`, preserve access and refresh cookie path/scope, and add focused OIDC, SAML ACS, and failed-callback no-cookie controller tests.
  - Validation: focused identity controller tests, `.\mvnw.cmd -pl services/identity-service -am test`, Codex validation, repo artifact hygiene, and `git diff --check` passed.
  - Prevention: keep per-cookie SSO callback tests when adding new federation paths, and consider extracting shared auth-cookie construction if cookie logic changes again.
- 2026-05-20: Delivery provider ownership stopped at tenant/provider IDs.
  - Symptom: delivery provider CRUD, selection, health, capacity, and failover paths could reference provider configuration without enforcing same-workspace ownership.
  - Source evidence: `SmtpProviderRepository.java`, `RoutingRuleRepository.java`, `ProviderSelectionStrategy.java`, `ProviderHealthMonitoringService.java`, `ProviderCapacityService.java`, and `V1__delivery_schema.sql`/`V3__provider_health_and_replay.sql`.
  - Causal chain: provider tables were originally tenant-scoped, while later workspace-aware delivery paths stored `workspace_id` on health and operational tables without a composite provider ownership boundary.
  - Fix: added workspace ownership to providers, routing rules, and IP pools; made provider CRUD/selection/orchestration/health/capacity/failover lookups tenant+workspace scoped; added V16 backfill, indexes, uniqueness, checks, and composite FKs for provider-linked tables.
  - Validation: focused migration, controller, repository, selection, operations, health, capacity, orchestration tests, `.\mvnw.cmd -pl services/delivery-service -am test`, Codex validation, repo artifact hygiene, and `git diff --check` passed.
  - Prevention: any provider-linked delivery table must carry tenant+workspace ownership in both service lookups and database constraints.
- 2026-05-20: Admin settings trusted request-body scope over current context.
  - Symptom: admin settings validate/apply/reset accepted request-body `workspaceId` and `environmentId` when present, even if they differed from `TenantContext`.
  - Source evidence: `AdminSettingsService.java`, `AdminSettingsServiceTest.java`, foundation service audit.
  - Causal chain: `WorkspaceContextFilter` guarded query parameters, but service methods preferred body fields over the current context and did not mirror mismatch checks used by compliance/performance foundation services.
  - Fix: added service-level context comparison helpers, validation errors for mismatches, fail-closed apply/reset/impact behavior, and reset required-context checks.
  - Validation: focused `AdminSettingsServiceTest`, full `.\mvnw.cmd -pl services/foundation-service -am test`, and `git diff --check` passed.
  - Prevention: any foundation request body carrying workspace/environment scope must compare explicit values to `TenantContext` before repository/service side effects.
- 2026-05-20: Config update/delete resolved raw IDs before ownership checks.
  - Symptom: known config IDs could reach update/delete lookup without first proving current tenant, workspace, and environment ownership.
  - Source evidence: `ConfigService.java`, `ConfigRepository.java`, `ConfigServiceTest.java`, foundation service audit.
  - Causal chain: `updateConfig` and `deleteConfig` used raw `findById`, while tenant context checks happened after entity resolution and global/null-tenant config handling was not separated into a privileged workflow.
  - Fix: added tenant-scoped non-deleted repository lookup, routed mutable by-ID operations through it, and fail-closed workspace/environment scope mismatches or missing scoped context before save/delete.
  - Validation: focused `ConfigServiceTest`, full `.\mvnw.cmd -pl services/foundation-service -am test`, and `git diff --check` passed.
  - Prevention: by-ID mutation paths must use repository ownership predicates before loading mutable entities; global administration needs an explicit privileged path instead of sharing tenant endpoints.
- 2026-05-20: Differentiation upsert lookup treated null workspace as a wildcard.
  - Symptom: tenant-scoped or no-workspace differentiation upserts could match any workspace row with the same key before update.
  - Source evidence: `DifferentiationPlatformService.java`, `DifferentiationPlatformServiceTest.java`, foundation service audit.
  - Causal chain: `upsertByKey` used `(:workspaceId IS NULL OR workspace_id = :workspaceId)` for lookup, then fell back to tenant-only `updateById` when `workspaceId` was null.
  - Fix: changed lookup to `COALESCE(workspace_id, '') = COALESCE(:workspaceId, '')` and added focused tests for null workspace context and current workspace context.
  - Validation: focused `DifferentiationPlatformServiceTest`, full `.\mvnw.cmd -pl services/foundation-service -am test`, and `git diff --check` passed.
  - Prevention: upsert lookup predicates must match the same ownership scope as the intended insert/update target; null workspace must mean tenant-scope, not wildcard.
- 2026-05-20: Differentiation evaluate paths kept the old null-workspace wildcard.
  - Symptom: decision-policy evaluation, omnichannel simulation, and SLO evaluation could select the newest matching workspace row in the current tenant when workspace context was absent.
  - Source evidence: six read-only foundation differentiation scouts, `DifferentiationPlatformService.java`, `DifferentiationPlatformServiceTest.java`, and `V13__phase4_differentiation_platform.sql`.
  - Causal chain: upsert lookup had moved to exact nullable workspace matching, but evaluate/simulate reads still used `(:workspaceId IS NULL OR workspace_id = :workspaceId)` while inserting decision/run/incident records under the selected row's workspace.
  - Fix: changed all three evaluate/simulate lookups to `COALESCE(workspace_id, '') = COALESCE(:workspaceId, '')` and added SQL/params capture tests for current workspace and missing-workspace contexts.
  - Validation: focused `DifferentiationPlatformServiceTest` passed with 9 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 154 foundation-service tests plus upstream shared modules, Codex validation/monitor/lease checks passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: differentiation read/evaluate queries must use the same ownership predicate as matching write/upsert paths; tenant/global inheritance needs explicit policy and tests rather than implicit null wildcard behavior.
- 2026-05-20: Public contact admin access exposed a global PII table to tenant/org admins.
  - Symptom: admin contact request list/status endpoints allowed `ADMIN` and `ORG_ADMIN` roles even though `public_contact_requests` has no tenant/workspace ownership columns.
  - Source evidence: `AdminContactRequestController.java`, `PublicContactService.java`, `PublicContactRequest.java`, `AdminContactRequestControllerSecurityTest.java`, foundation service audit.
  - Causal chain: the controller used the same broad admin role expression as tenant-scoped admin surfaces, but the backing contact table is global and stores public-contact PII.
  - Fix: restricted the controller to `hasRole('PLATFORM_ADMIN')` and added reflection tests that fail if class-level or method-level authorization is widened.
  - Validation: focused contact tests, full `.\mvnw.cmd -pl services/foundation-service -am test`, and `git diff --check` passed.
  - Prevention: global PII admin surfaces must require platform-admin authority until schema-backed tenant/workspace ownership and product semantics are implemented.
- 2026-05-20: Config create/upsert trusted body scope before context comparison.
  - Symptom: config create/upsert could accept request-body `workspaceId` or `environmentId` values before proving they matched the current `TenantContext`.
  - Source evidence: `ConfigService.java`, `ConfigServiceTest.java`, foundation service audit.
  - Causal chain: update/delete had by-ID ownership checks, but create/upsert still used explicit body scope and trusted args without consistently rejecting body/context mismatches before lookup/save.
  - Fix: added fail-closed workspace/environment mismatch checks before repository lookup or save, while allowing explicit scoped calls only when trusted args or `TenantContext` match.
  - Validation: focused `ConfigServiceTest`, full `.\mvnw.cmd -pl services/foundation-service -am test`, and `git diff --check` passed.
  - Prevention: create/upsert paths carrying workspace/environment scope must compare body scope with trusted context before any lookup or mutation.
- 2026-05-20: Tenant get-by-ID did not enforce current tenant before lookup.
  - Symptom: a caller with `tenant:read` could request a known tenant ID that did not match the current `TenantContext`.
  - Source evidence: `TenantService.java`, `TenantServiceTest.java`, and foundation tenant lifecycle audit.
  - Causal chain: tenant lifecycle policy was still broad, and `getTenant` delegated directly to `findById` before proving the request was for the current tenant.
  - Fix: require `TenantContext.requireTenantId()` and return not-found for any path tenant ID that differs before repository lookup.
  - Validation: focused `TenantServiceTest`, full `.\mvnw.cmd -pl services/foundation-service -am test`, and `git diff --check` passed.
  - Prevention: tenant lifecycle endpoints must define explicit self-tenant versus platform-admin ownership before loading tenant records by supplied identifiers.
- 2026-05-20: Data-extension relationship and preview governance relied on loose JSON metadata.
  - Symptom: data-extension create/update and query preview accepted nested metadata with limited cascade validation; relationship definitions did not prove supported cardinality, type compatibility, or key compatibility; preview could sort after projection and segment rules could silently carry data-extension relationship metadata as custom fields.
  - Source evidence: `DataExtensionDto.java`, `DataExtensionService.java`, `SegmentService.java`, `DataExtensionServiceTest.java`, and `SegmentServiceTest.java`.
  - Causal chain: Contact Builder data-extension metadata stayed JSON-backed, while service validation focused on existence checks and did not enforce relationship/sendable/query-preview invariants before persistence or preview execution.
  - Fix: added nested DTO validation and payload caps, service-side relationship cardinality/type/key checks, sendable required/primary-key governance with change locks after records exist, upfront preview field/filter/sort validation with relationship-path rejection, sort-before-projection behavior, and segment rejection for unsupported data-extension relationship metadata.
  - Validation: focused audience data-extension/segment tests, full `.\mvnw.cmd -pl services/audience-service -am test`, Codex validation, repo artifact hygiene, and scoped `git diff --check` passed.
  - Prevention: relationship joins, provenance, classification, and audit must move into additive schema-backed slices with focused migration and controller/API tests before Contact Builder parity claims.
- 2026-05-20: Foundation core-platform creation paths checked tenant existence but not workspace ownership.
  - Symptom: team, department, membership, role binding, access grant, and permission-group creation could accept related IDs that were valid for the tenant but not valid for the current workspace or tenant request context.
  - Source evidence: `CorePlatformService.java`, `V6__platform_core_foundation.sql`, six read-only foundation scouts, and `CorePlatformServiceTest.java`.
  - Causal chain: platform core records are SQL-map backed instead of JPA entities, so helper validation stopped at `id + tenant_id`; workspace relationships were left to nullable foreign keys and controller RBAC instead of service-level ownership checks.
  - Fix: added workspace/context helpers, same-workspace row checks for teams/departments/memberships, principal/resource workspace checks for role bindings, grantee membership checks for access grants, and tenant mismatch denial for permission groups.
  - Validation: focused `CorePlatformServiceTest` passed with 28 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 138 foundation-service tests plus upstream shared modules, Codex validation/monitor/lease checks passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: core-platform relationship mutations must prove tenant and workspace ownership at service boundaries before persistence; list/preview read scoping needs the next leased slice.
- 2026-05-20: Foundation core-platform read paths stayed tenant-wide after mutation guards.
  - Symptom: workspace-readable callers could list tenant-wide principal role bindings, delegated access grants, and preview access policy rows even after creation paths were workspace-guarded.
  - Source evidence: `CorePlatformService.java`, `CorePlatformController.java`, six read-only foundation read-scope scouts, `CorePlatformServiceTest.java`, and `CorePlatformControllerTest.java`.
  - Causal chain: controller methods called tenant-wide no-arg service reads, while role information was only used for mutation authorization; the SQL-map read helpers had no role-aware workspace branch.
  - Fix: added role-aware service overloads, exact-workspace read predicates for workspace-scoped callers, fail-closed missing-workspace checks, controller role plumbing, and focused service/controller regression tests.
  - Validation: focused `CorePlatformServiceTest,CorePlatformControllerTest` passed with 45 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 150 foundation-service tests plus upstream modules, Codex validation/monitor/lease checks passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: foundation read endpoints that expose access-control state must explicitly distinguish tenant-wide from workspace-scoped roles and must not rely on mutation guards to protect list/preview surfaces.
- 2026-05-20: Compliance privacy-request status update resolved by tenant and raw ID only.
  - Symptom: `/api/v1/compliance/privacy-requests/{id}/status` required workspace context at the controller boundary, but the service update path did not scope the mutation to the current workspace before writing immutable audit evidence.
  - Source evidence: `ComplianceEvidenceService.java`, `ComplianceEvidenceServiceTest.java`, six compliance privacy-request scouts, and `CorePlatformRepository.updateByIdAndWorkspace`.
  - Causal chain: `updatePrivacyRequest` required tenant context and called tenant-only `updateById`; the audit record was created after that mutation, so a known in-tenant request ID from another workspace could be updated and audited under the caller's context.
  - Fix: require `TenantContext.requireWorkspaceId()`, update with `updateByIdAndWorkspace("privacy_requests", id, tenantId, workspaceId, ...)`, and only insert audit evidence after the scoped update succeeds.
  - Validation: focused `ComplianceEvidenceServiceTest` passed with 5 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 157 foundation-service tests plus upstream modules, Codex validation/monitor/lease checks passed, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: compliance mutations that write audit evidence must prove the same tenant/workspace ownership used by the request context before any status/data change; nullable or tenant-global privacy request handling needs explicit admin policy.
- 2026-05-20: Core-platform nullable permission lists used object JSON defaults.
  - Symptom: role definition, permission group, and delegated access grant requests could omit `permissions`, and the service would persist `{}` into JSONB array-shaped columns.
  - Source evidence: six read-only permission-list scouts, `CorePlatformService.java`, `CorePlatformDto.java`, `CorePlatformRepository.java`, `V6__platform_core_foundation.sql`, and `CorePlatformServiceTest.java`.
  - Causal chain: request DTO permission fields are nullable, repository inserts always include provided JSON columns and bypass DB defaults, and the generic `toJson(null)` helper intentionally returns `{}` for object-shaped metadata/details payloads.
  - Fix: added a permission-list-specific `toJsonArray` helper and routed only role-definition, permission-group, and access-grant permission fields through it, preserving generic object defaulting for metadata.
  - Validation: focused `CorePlatformServiceTest` passed with 41 tests, full `.\mvnw.cmd -pl services/foundation-service -am test` passed with 161 foundation-service tests plus upstream modules, V6 migration diff check passed, Codex validation/monitor/lease checks passed, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: JSON helpers must match the target column shape; do not reuse object-defaulting helpers for array-shaped fields without focused null-shape tests.
- 2026-05-21: Campaign feedback counters used read-modify-save mutation.
  - Symptom: sent/failed delivery feedback updated send-job and send-batch counters by mutating loaded entities, so concurrent feedback events could overwrite each other's increments.
  - Source evidence: `CampaignEventConsumer.java`, `SendJobRepository.java`, `SendBatchRepository.java`, `CampaignEventConsumerTest.java`, and `CampaignFeedbackCounterRepositoryTest.java`.
  - Causal chain: idempotency protected duplicate event IDs, but counter mutation still depended on stale entity state instead of database-side scoped increments.
  - Fix: added tenant/workspace/id-scoped repository update queries for send-job sent/failed counters and send-batch processed/success/failure/status counters, then reloaded the job before campaign reconciliation.
  - Validation: focused campaign event/repository tests passed with 25 tests, full campaign-service reactor tests passed with 99 campaign-service tests plus upstream shared modules, Codex validation passed, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: high-volume feedback counters should use scoped atomic repository updates plus focused execution tests for generated update queries; local tests do not replace target replay and contention evidence.
- 2026-05-21: Release evidence self-test child validators could hang indefinitely.
  - Symptom: negative-fixture validator runs used `Start-Process -Wait`, so a stalled child command could block the whole self-test and leave child processes behind.
  - Source evidence: `scripts/ops/test-release-evidence-validators.ps1` and release/devops scout validation that timed out while running the self-test.
  - Causal chain: positive fixtures ran inline, but negative fixtures needed subprocess isolation for expected failures; those subprocesses had no per-command timeout or process-tree cleanup.
  - Fix: added a shared validator-process helper with a configurable per-child timeout, captured output excerpts, process-tree termination, and consistent use by egress, render, GA, and image validator fixture calls.
  - Validation: release evidence validator self-test passed, local-only release gate passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: CI-facing validator harnesses should bound child commands and terminate descendants on timeout; strict promotion evidence requirements must remain separate from local self-test safety.
- 2026-05-21: Delivery replay queue processing used unbounded read and entity-save claims.
  - Symptom: replay processing loaded all due PENDING rows, stopped only after an in-memory counter, saved `PROCESSING` without a status predicate, and did not include source `contentReference` in replay payloads.
  - Source evidence: `DeliveryOperationsService.java`, `DeliveryReplayQueueRepository.java`, and `DeliveryOperationsServiceWorkspaceScopeTest.java`.
  - Causal chain: replay was implemented as a manual operations helper, while high-volume send paths later required durable content references and compare-and-claim processing.
  - Fix: added pageable due-row lookup, tenant/workspace/id/status-scoped claim/complete/fail update queries, claim-skip behavior, source content-reference propagation, and fail-closed replay rows when the source content reference is missing.
  - Validation: focused delivery operations tests passed with 8 tests, full delivery-service reactor tests passed with 90 delivery-service tests plus upstream shared modules, Codex validation passed, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: replay and retry operations must use bounded pages, scoped compare-and-claim updates, and the same content-reference contract as live delivery handoff.
- 2026-05-21: Tracking outbox enqueue still published just-created rows inline.
  - Symptom: public tracking ingestion persisted an outbox row and then synchronously attempted to publish the same row after commit or inline when transaction synchronization was inactive.
  - Source evidence: `TrackingOutboxService.java`, `TrackingOutboxServiceTest.java`, tracking outbox/ingestion/publisher focused tests, and full tracking-service reactor validation.
  - Causal chain: the outbox poller existed, but enqueue still carried a compatibility-style immediate publish path, so Kafka health could affect request completion instead of being isolated to the durable poller/claim/retry workflow.
  - Fix: defaulted enqueue to persistence-only behavior, kept the poller as the publisher, preserved claim/retry/failure handling, and added a guarded compatibility flag for immediate publish rollback.
  - Validation: focused tracking outbox/ingestion/publisher tests passed with 14 tests, full tracking-service reactor tests passed with 72 tests and 7 expected Testcontainers skips, Codex validation passed, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: public ingestion paths should acknowledge durable persistence and leave external broker publication to bounded outbox workers unless a documented compatibility flag is intentionally enabled.
- 2026-05-21: Legacy reputation fallback used domain-only access after workspace columns existed.
  - Symptom: reputation reads could fall back from scoped `domain_reputations` to `reputation_scores` by domain alone, and legacy reputation updates wrote rows without tenant/workspace ownership.
  - Source evidence: `ReputationController.java`, `ReputationScore.java`, `ReputationScoreRepository.java`, `ReputationService.java`, `V7__workspace_scope_and_event_idempotency.sql`, and reputation focused tests.
  - Causal chain: V7 added tenant/workspace/source columns to the legacy table for transition compatibility, but the JPA entity, repository, controller fallback, and update service kept the pre-scope domain-only contract and the V1 global domain uniqueness constraint.
  - Fix: added tenant/workspace fields to the entity, replaced domain-only repository methods with scoped latest-row lookup, normalized domains at read/write boundaries, wrote legacy rows with `TenantContext` ownership, and added V11 to remove the old global same-domain uniqueness constraint and index scoped latest lookup.
  - Validation: focused reputation/RBAC tests passed with 19 tests, full deliverability-service reactor tests passed with 59 deliverability-service tests plus upstream shared modules, repo artifact hygiene passed, JPA config scan passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: compatibility tables that receive tenant/workspace columns must update entity/repository/service contracts in the same slice, and target Flyway migration evidence must review legacy rows before promotion.
- 2026-05-21: Campaign checkpoint and resume paths stayed tenant-only after send jobs became workspace-owned.
  - Symptom: send-job checkpoints could be created/read/resumed through tenant/job-only access, and resumed jobs did not carry workspace ownership from the original job.
  - Source evidence: `SendJobCheckpointingService.java`, `SendJobCheckpointRepository.java`, `SendJobCheckpoint.java`, `V2__campaign_approval_and_checkpoint.sql`, `V11__campaign_workspace_lifecycle_and_idempotency.sql`, and checkpoint focused tests.
  - Causal chain: V11 added workspace ownership to send jobs and batches, but checkpoint/resume/recovery compatibility tables and the checkpoint service kept older tenant-only repository methods.
  - Fix: added checkpoint workspace ownership, scoped repository methods/counts by tenant+workspace+job, required current workspace context for checkpoint/resume/progress/retry paths, copied workspace/team ownership into resumed jobs, and added V16 to backfill and index checkpoint/resume/recovery workspace columns.
  - Validation: focused campaign checkpoint/send-execution/consumer/scheduler tests passed with 54 tests, full campaign-service reactor tests passed with 106 campaign-service tests plus upstream shared modules, repo artifact hygiene passed, JPA config scan passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: when primary lifecycle entities gain workspace ownership, compatibility/recovery tables and resume services must be scoped in the same ownership model before release.
- 2026-05-22: Audience import processing retained unbounded row-error details until completion.
  - Symptom: high-error CSV imports could append every row-error detail from each chunk into one in-memory list before final truncation.
  - Source evidence: `ImportProcessingService.java`, `AppConstants.IMPORT_CHUNK_SIZE`, `AppConstants.IMPORT_MAX_ERRORS`, and `ImportProcessingServiceTest.java`.
  - Causal chain: chunk processing tracked full `errorCount`, but used the same unbounded list for detailed error samples and only applied the configured maximum when writing the completed import job.
  - Fix: pass remaining error-sample capacity into chunk processors, count every failed row, add detail samples only up to `AppConstants.IMPORT_MAX_ERRORS`, and persist the already-bounded list.
  - Validation: focused import tests passed with 9 tests, full audience-service reactor tests passed with 138 audience-service tests plus upstream shared modules, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: high-volume import paths must separate aggregate counters from retained detail samples during chunk processing, not only at final persistence.
- 2026-05-22: Identity invitation listing used tenant-only scope.
  - Symptom: an authenticated user with invitation administration permission could list invitation metadata for all workspaces in the same tenant.
  - Source evidence: `AuthController.java`, `AuthService.java`, `AuthInvitationRepository.java`, and identity service/controller focused tests.
  - Causal chain: invitation creation and acceptance carried workspace ownership, but the list API kept an older tenant-only repository lookup and did not require the authenticated principal to carry workspace context.
  - Fix: replace the tenant-only repository lookup with tenant+workspace access, require workspace context in `AuthService.listInvitations`, and have `AuthController.listInvitations` pass the authenticated principal workspace while failing closed when it is absent.
  - Validation: focused identity service/controller tests passed with 32 tests, full identity-service reactor tests passed with 51 identity-service tests plus upstream shared modules, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: list/admin endpoints for workspace-owned identity resources must take workspace identity from trusted auth context and must reject missing workspace context before repository access.
- 2026-05-22: Deliverability internal suppression route security-chain coverage was missing.
  - Symptom: deliverability internal suppression list/check endpoints had controller and RBAC reflection tests, but no Spring security-chain test proving the intended anonymous route pass-through plus normal workspace route protection.
  - Source evidence: `SecurityConfig.java`, `SuppressionController.java`, `SuppressionControllerTest.java`, `DomainControllerRbacTest.java`, and new `SecurityConfigTest.java`.
  - Causal chain: the controller owns the internal credential guard, while `SecurityConfig` must allow those internal paths to reach it; without a filter-chain regression test, future matcher changes could silently block service-to-service access or over-broaden public access.
  - Fix: add a minimal Spring security test app covering anonymous access to the two internal suppression routes, authentication requirement for a normal deliverability route, and method-security deny/allow behavior for representative `deliverability:read` access.
  - Validation: focused security-chain tests passed with 5 tests, full deliverability-service reactor tests passed with 64 deliverability-service tests plus upstream shared modules, route-map validation passed, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: every service-internal endpoint should have both public-edge route validation and service-local security-chain tests for the intended guard model.
- 2026-05-22: Delayed automation resumes ignored scheduled workspace scope.
  - Symptom: a delayed workflow wake job stored tenant/workspace data but resumed the engine using only instance ID, next node ID, and wake ID.
  - Source evidence: `DelayNodeHandler.java`, `WorkflowQuartzJob.java`, `WorkflowEngine.java`, `WorkflowInstanceRepository.java`, `WorkflowEngineTest.java`, and `WorkflowQuartzJobTest.java`.
  - Causal chain: the delay scheduler already preserved scope in Quartz job data, but the Quartz job did not propagate it and the engine loaded the workflow instance by raw ID before setting `TenantContext` from the loaded row.
  - Fix: pass tenant/workspace from `WorkflowQuartzJob` into `WorkflowEngine.resumeInstance`, require both values before repository access, and load the instance with `findByIdAndTenantIdAndWorkspaceId` before idempotency, lock acquisition, execution, or save.
  - Validation: focused workflow engine/Quartz tests passed with 9 tests, full automation-service reactor tests passed with 82 automation-service tests plus upstream shared modules, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: scheduled and recovery callbacks must carry trusted scope into service entry points and must not derive tenant/workspace only after a raw ID lookup.
- 2026-05-22: GA and image release evidence validators accepted unversioned manifests.
  - Symptom: GA/image evidence templates emitted `schemaVersion = 1`, but validators parsed manifests without rejecting missing or unsupported schema versions.
  - Source evidence: `scripts/ops/write-ga-evidence-manifest-template.ps1`, `scripts/ops/write-image-supply-chain-checklist.ps1`, `scripts/ops/validate-ga-evidence.ps1`, `scripts/ops/validate-image-evidence.ps1`, and `scripts/ops/test-release-evidence-validators.ps1`.
  - Causal chain: egress evidence had an explicit version contract, while GA/image validation focused on path and image artifact completeness, so older or malformed evidence contracts could pass local validation by accident.
  - Fix: require `schemaVersion` 1 in GA and image validators, add missing/unsupported version fixtures to the release evidence self-test, and document the manifest contract in the GA evidence matrix.
  - Validation: release evidence validator self-test passed with the 120s child-process timeout, local-only release gate passed, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: every release evidence manifest family needs an explicit version check plus negative fixtures before strict release gates can rely on it.
- 2026-05-22: Subscriber by-ID service operations loaded rows before scope was proven.
  - Symptom: subscriber get, update, and delete accepted an ID, loaded the row by raw ID, and only then filtered tenant/workspace/deleted status in memory.
  - Source evidence: `SubscriberService.java`, `SubscriberRepository.java`, and `SubscriberServiceTest.java`.
  - Causal chain: later merge/bulk helper paths adopted the scoped repository method, but original CRUD paths kept older raw ID lookups, creating avoidable cross-workspace row fetches and mutation paths dependent on in-memory filters.
  - Fix: replace raw `findById` calls with `findByTenantIdAndWorkspaceIdAndId` for get/update/delete and add tests for cache hit preservation, scoped cache miss, scoped update, and denied update/delete misses.
  - Validation: focused `SubscriberServiceTest` passed with 9 tests, full audience-service reactor tests passed with 142 audience-service tests plus upstream shared modules, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: workspace-owned entities should use scoped repository methods at lookup time rather than raw ID load plus post-filtering.
- 2026-05-22: Alert runbook links could point at missing local targets.
  - Symptom: production Prometheus alert annotations referenced a local production hardening runbook and anchors that were not present in the repository.
  - Source evidence: `infrastructure/kubernetes/observability/prometheus-alerts.yml`, `scripts/ops/validate-production-overlay.ps1`, and `docs/operations/production-hardening-runbook.md`.
  - Causal chain: production overlay validation checked core Kustomize, NetworkPolicy, and image posture, but did not validate observability annotations that operators rely on during incidents.
  - Fix: add the production hardening runbook and make production overlay validation check repository-relative local `runbook_url` files and Markdown anchors, with a parameterized alert file path for negative fixtures.
  - Validation: production overlay validation passed, negative missing-runbook and missing-anchor fixtures failed as expected, local-only release gate passed, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: alert-rule changes should validate local runbook links and anchors in the same local release gate path.
- 2026-05-22: Audience import frontend status handling drifted from backend lifecycle values.
  - Symptom: import list badges did not classify `VALIDATING` or `PROCESSING`, details polling stopped for `VALIDATING`, and the wizard kept polling `CANCELLED` while rendering terminal failures generically.
  - Source evidence: `frontend/src/app/(workspace)/audience/imports/page.tsx`, `frontend/src/app/(workspace)/audience/imports/new/page.tsx`, `frontend/src/app/(workspace)/audience/imports/[id]/page.tsx`, and backend `ImportJob` status values.
  - Causal chain: frontend import UI carried older status assumptions while backend exposed a broader import lifecycle, and there was no workflow-specific Playwright coverage for upload, polling, or terminal states.
  - Fix: classify active and terminal states consistently across list, wizard, and details; make `VALIDATING` poll as active; stop polling `CANCELLED`; render distinct terminal states; add targeted Playwright lifecycle coverage.
  - Validation: frontend lint passed, frontend production build passed, targeted Chromium Playwright import lifecycle spec passed with 6 tests, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: visible workflow state machines should share backend lifecycle terms in tests, especially when polling and terminal actions differ by status.
- 2026-05-22: Content GET RBAC coverage was limited to unsafe mappings.
  - Symptom: authenticated users could reach protected content read endpoints without method-level read permission checks.
  - Source evidence: `EmailStudioController.java`, `TemplateController.java`, `ContentBlockController.java`, `TemplateVersionController.java`, `TemplateWorkflowController.java`, `EmailController.java`, `SecurityConfig.java`, and `ContentControllerRbacTest.java`.
  - Causal chain: the content RBAC reflection test checked POST/PUT/PATCH/DELETE mappings only, while the security chain authenticated unmatched routes and left read authorization to controller annotations. Older GET handlers were never brought under the content read permission pattern.
  - Fix: add `content:read` or `template:*` authorization to protected GET handlers and expand reflection coverage to all protected GET mappings, with explicit exceptions for public landing-page and guarded internal reads.
  - Validation: focused `ContentControllerRbacTest` passed with 7 tests, full content-service reactor tests passed with 54 content-service tests plus upstream shared modules, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: future content read routes must either declare read RBAC or be deliberately added to the public/internal exception list with matching security-chain evidence.
- 2026-05-22: Audience tracking intelligence consumed high-volume tracking events one record at a time.
  - Symptom: audience-service listened to `tracking.ingested` with the shared record-ack Kafka listener while tracking-service already used a batch listener for the same high-volume topic.
  - Source evidence: `AudienceIntelligenceConsumer.java`, shared `KafkaConsumerConfig.java`, tracking `TrackingKafkaConsumerConfig.java`, and focused audience consumer/config tests.
  - Causal chain: subscriber intelligence started as per-event enrichment and reused the shared Kafka factory, so it inherited record ack and no bounded batch settings even as tracking ingestion became batch-oriented.
  - Fix: add an audience-scoped batch listener factory, move tracking consumption to `consumeTrackingBatch`, preserve single-event processing for tests and retry semantics, skip invalid/unsupported and in-batch duplicate events before side effects, and rethrow service failures for Kafka retry/DLQ handling.
  - Validation: focused `AudienceIntelligenceConsumerTest,AudienceKafkaConsumerConfigTest,SubscriberIntelligenceServiceTest` passed with 14 tests, full audience-service reactor tests passed with 148 audience-service tests plus upstream shared modules, repo artifact hygiene passed, Codex validation passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: high-volume topic consumers should use explicit module-owned listener factories and tests for ack mode, poll bounds, duplicate handling, invalid-event behavior, and retry behavior.
- 2026-05-22: AI content assistance governance stopped at foundation policy/audit and was not yet enforced in content draft/test-send workflow.
  - Symptom: foundation-service could evaluate draft-only AI assistance policy, but content-service had no contract for applying reviewed AI draft evidence or blocking unresolved AI assistance metadata before publish/test-send side effects.
  - Source evidence: `AiContentAssistanceGovernanceServiceTest.java`, `TemplateWorkflowService.java`, `TemplateTestSendService.java`, `TemplateWorkflowDto.java`, and new content workflow/test-send tests.
  - Causal chain: the first governance slice deliberately avoided content workflow integration and provider calls, leaving the draft application boundary as a follow-up to avoid storing raw prompts/outputs or implying model-backed generation readiness.
  - Fix: add an `AiDraftApplication` DTO, apply only approved `APPLY_TO_DRAFT` evidence with human review and SHA-256 hashes, store hash/reference metadata only, and require resolved AI metadata before publish/test-send rendering or Kafka publication.
  - Validation: focused foundation governance tests passed with 10 tests, focused content workflow/test-send tests passed with 5 tests, full foundation+content reactor tests passed with 59 target-service tests plus upstream shared modules, repo artifact hygiene passed, and scoped `git diff --check` passed with CRLF warnings only.
  - Prevention: AI-generated content paths must keep provider invocation, raw content storage, policy/audit verification, draft application, publish, and send/test-send side effects as separately reviewed contracts with fail-closed tests.
- 2026-05-22: Latest audit found cross-module local gaps after prior fixes.
  - Symptom: local validation and route coverage still missed some operational contracts: alert runbook completeness, Compose env determinism, unresolved delivery `workspace-default` rows, forgot-password ambiguous tenant/workspace resolution, stale non-throwing tracking publishers, SES endpoint parsing, subscriber bulk-action/query encoding, and landing-page public sanitizer route coverage.
  - Source evidence: read-only latest audit scouts, `prometheus-alerts.yml`, `release-gate.ps1`, delivery V16 workspace migration, `IdentityExperienceService.java`, `TrackingEventPublisher.java`, `AwsSesProviderAdapter.java`, subscriber page source, and landing-page studio/public routes.
  - Causal chain: earlier hardening slices fixed primary service behavior, but compatibility helpers, release script defaults, legacy migration follow-up guards, and frontend contract tests lagged behind the stricter workspace/release/sanitization model.
  - Fix: add missing runbook URLs and validation, require `.env.example` through `ComposeEnvFile`, add delivery V17 fail-fast legacy guard, scope forgot-password reset side effects to explicit/unambiguous tenant plus active workspace membership, keep only the throwing tracking publisher path, harden SES region resolution, switch subscriber bulk delete to backend bulk action with encoded queries, and add focused landing-page E2E coverage.
  - Validation: focused delivery, identity, and tracking tests passed; full delivery+identity+tracking backend gate passed; frontend lint/build and focused Playwright passed; release/SRE validators, local-only release gate, artifact hygiene, Codex validation, and `git diff --check` passed.
  - Prevention: latest-audit follow-ups should be promoted as one checkpointed closure only when their validation spans each touched boundary; production claims still require target evidence and blocked items stay blocked.

Root-cause entries must include:
- symptom,
- source evidence,
- causal chain,
- fix,
- validation,
- prevention,
- related memory links.
- 2026-05-22: Campaign send execution loaded campaigns by tenant only after a workspace-scoped batch claim.
  - Symptom: `SendExecutionService.executeBatch` required the send batch workspace and claimed the batch with tenant/workspace/job/batch scope, but then fetched the campaign by tenant+campaign ID only before rendering and delivery handoff.
  - Source evidence: `SendExecutionService.java`, `CampaignRepository.java`, and `SendExecutionServiceTest.java`.
  - Causal chain: send batches gained workspace ownership and scoped claiming, while the campaign lookup kept an older tenant-only repository method. A malformed or legacy batch row could therefore couple a workspace-owned batch to a same-tenant campaign in another workspace.
  - Fix: replace the tenant-only campaign lookup with `findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull` using the claimed batch workspace, and fail the batch before rendering or send publication if no scoped campaign exists.
  - Validation: focused regression and full campaign-service test gate passed locally; repo artifact hygiene, Codex validation, and scoped diff checks passed.
  - Prevention: once a worker claims a workspace-owned lifecycle row, all downstream entity lookups in the send path must keep using that trusted workspace scope before side effects.
- 2026-05-22: Parallel safe local slices exposed integration-time scope, URL, and release validator gaps.
  - Symptom: completed slices were individually passing but review found send-time DNS in delivery click rewriting, active-only subscriber-key schema drift between JPA metadata and Flyway, brittle frontend sanitizer/auth tests, and release validators that could miss strict compose safety or per-container production posture.
  - Source evidence: `OutboundUrlGuard.java`, `ContentProcessingService.java`, `IngestionController.java`, `Subscriber.java`, `V19__workspace_scope_subscriber_key_uniqueness.sql`, frontend auth/sanitize E2E specs, `release-gate.ps1`, `validate-production-overlay.ps1`, and `test-release-evidence-validators.ps1`.
  - Causal chain: narrow subagent slices fixed local concerns independently, but integration review caught cross-boundary hot-path performance, schema-source-of-truth, test brittleness, and validator false-negative risks before completion.
  - Fix: add syntax-only URL guard for send-time rewriting, enforce full public URL validation at click redirect, make Flyway the subscriber uniqueness source of truth, harden frontend tests, run compose safety in release-gate, and validate rendered Deployment containers individually.
  - Validation: focused backend/frontend/release gates, route harnesses, local-only release gate, repo artifact hygiene, Codex validation, and diff checks passed locally.
  - Prevention: keep integration review as a required step after parallel subagent output; hot-path guards, schema metadata, and release validators need cross-slice review before marking batch work done.
- 2026-05-22: Backend workspace-scope safety gaps remained in scheduled automation, deliverability domain operations, and content events.
  - Symptom: schedule trigger jobs could trust stale Quartz data, sender-domain operations could begin from ID lookups without workspace ownership proof, and content template events did not consistently carry envelope workspace scope.
  - Source evidence: `WorkflowScheduleTriggerJob.java`, `DomainController.java`, `DomainVerificationService.java`, `ContentEventPublisher.java`, `TemplateWorkflowService.java`, `TemplateVersionService.java`, and `EventContractValidator.java`.
  - Causal chain: earlier tenant/workspace hardening reached primary service APIs, but scheduled jobs, operational side effects, and event-envelope metadata kept older assumptions where tenant ID or object ID was enough.
  - Fix: load active schedules by schedule+tenant+workspace before publish, resolve sender domains by tenant+workspace+ID before DNS/save/history, pass workspace through content workflow publications, and validate `content.published` workspace metadata in the shared Kafka contract.
  - Validation: focused automation, deliverability, and content/shared Kafka tests passed; integrated backend module gate passed locally.
  - Prevention: scheduled jobs, side-effecting service operations, and cross-service events must prove workspace scope at the boundary before context installation or publication.
- 2026-05-22: Local scouts found remaining unsafe defaults, unbounded fanout, and scope gaps in non-primary paths.
  - Symptom: platform webhook dispatch loaded every active webhook config and built all dispatch Monos at once; prod runtime guard did not block unsafe `ddl-auto`; Kafka trusted packages could be widened; tracking and delivery outbox publish paths claimed/reloaded by raw ID; frontend treated all `/api/v1/core` requests as workspace optional.
  - Source evidence: `WebhookDispatcherService.java`, `RuntimeConfigurationGuard.java`, `KafkaConsumerConfig.java`, `TrackingOutboxService.java`, `DeliveryFeedbackOutboxService.java`, and `api-client.ts`.
  - Causal chain: earlier hardening focused on primary request paths and event contracts, while scheduler/outbox/platform helper paths and frontend preflight rules retained broader or older assumptions.
  - Fix: page webhook dispatch, reject unsafe production DDL and broad Kafka trust, scope outbox claim/reload by tenant+workspace, and remove broad frontend core workspace-optional matching.
  - Validation: focused and full touched backend module gates passed; frontend targeted Playwright, lint, and production build passed.
  - Prevention: helper publishers, outbox pollers, runtime overrides, and frontend client preflight must be included in tenant/workspace and production-safety audits, not just controllers.
- 2026-05-22: Parallel discovery found secondary scope and boundedness gaps after the primary queue emptied.
  - Symptom: invitation creation accepted authenticated principals without workspace context; campaign approval mutations loaded approvals by raw ID before workspace proof; campaign dead-letter and automation run/history reads were unbounded; Compose health validation depended on ambient Docker Compose state; frontend treated platform notifications/webhooks as workspace optional.
  - Source evidence: `AuthController.java`, `CampaignWorkflowService.java`, `CampaignEngineService.java`, `WorkflowStudioService.java`, `validate-compose-health.ps1`, and `api-client.ts`.
  - Causal chain: previous fixes focused on send execution, scheduler, and outbox paths; operator read paths, admin invitation creation, local validation scripts, and frontend allowlists retained older broad defaults.
  - Fix: require workspace context before invitation side effects, scope approval lookup through owning campaign workspace, bound DLQ/run/history reads, make Compose health env-file deterministic, and narrow platform frontend preflight.
  - Validation: combined backend module gate, frontend Playwright/lint/build, Compose health checks, Codex validation, artifact hygiene, and diff check passed locally.
  - Prevention: when a module is hardened, also audit adjacent read/list endpoints, mutation lookup order, and local validation scripts for boundedness and exact context requirements.
- 2026-05-22: Public reset credential stayed in the address bar after the frontend captured it.
  - Symptom: the public reset screen read the credential from query parameters but left the query string visible in browser history/address state.
  - Source evidence: `frontend/src/components/marketing/PublicAuthViews.tsx` and `frontend/tests/e2e/marketing.spec.ts`.
  - Causal chain: the page handled credential submission but did not scrub the URL after first capture, and focused marketing E2E coverage did not assert history cleanup or storage boundaries.
  - Fix: capture the credential once into component state, replace the browser URL with the query-free reset route, preserve missing-credential behavior, and add Playwright assertions for submit payload and storage non-use.
  - Validation: targeted marketing Playwright, frontend lint, frontend production build, Codex validation, artifact hygiene, and diff check passed locally.
  - Prevention: public credential-bearing routes should scrub the URL immediately after capture and test both history state and browser-storage boundaries.
- 2026-05-22: Outbox backlog visibility stopped at publish/retry behavior.
  - Symptom: tracking and delivery feedback outboxes had durable publish/retry paths but lacked low-cardinality ready-depth and oldest-ready-age metrics, alert rules, dashboard panels, and runbook triage.
  - Source evidence: `TrackingOutboxService.java`, `DeliveryFeedbackOutboxService.java`, outbox repositories/tests, `prometheus-alerts.yml`, `grafana-legent-overview.json`, and `production-hardening-runbook.md`.
  - Causal chain: earlier durability and scope fixes improved correctness, but operational evidence still depended on logs/tests and did not expose backlog age/depth for monitoring handoff.
  - Fix: add queue-only backlog gauges, repository queries for ready count and oldest ready age, focused metric-label tests, Prometheus alerts, Grafana panels, and runbook guidance.
  - Validation: focused outbox tests, full delivery+tracking backend module gate, production overlay validation, Grafana JSON parse, Codex validation, artifact hygiene, and diff check passed locally.
  - Prevention: every durable queue/outbox should expose bounded cardinality backlog depth and age metrics before being treated as release-monitorable.
- 2026-05-22: Admin public-content operations could proceed without full service-level context.
  - Symptom: admin CMS list/upsert/publish normalized nullable tenant/workspace context and could reach repository methods with missing scope if service code was invoked without the HTTP filter guarantee.
  - Source evidence: `PublicContentService.java` and `PublicContentServiceTest.java`.
  - Causal chain: the route-level workspace filter protected the admin path, but the service reused permissive public-read normalization instead of fail-closed context requirements for admin side effects.
  - Fix: require tenant and workspace context in admin list/upsert/publish before lookup or save, with tests proving no repository interaction when context is missing.
  - Validation: focused service tests and integrated foundation+content backend gate passed locally.
  - Prevention: service-level admin/mutation methods should require context even when an HTTP filter also enforces it.
- 2026-05-22: Template test-send history used an unbounded repository list.
  - Symptom: template test-send history returned every matching row for a tenant/workspace/template.
  - Source evidence: `TemplateTestSendService.java`, `TemplateTestSendRecordRepository.java`, and `TemplateTestSendServiceTest.java`.
  - Causal chain: the endpoint preserved a simple list response shape but never moved the repository query to a bounded page as test-send records accumulated.
  - Fix: use a bounded first-page `PageRequest` in the service/repository path and add tests for default and max-clamped limits.
  - Validation: focused content service test and integrated foundation+content backend gate passed locally.
  - Prevention: operational history/list endpoints should apply default/max limits even when the external response remains a list.
- 2026-05-22: CI Compose smoke relied on ambient environment discovery.
  - Symptom: CI used bare Compose config validation while local release gates had moved to an explicit reviewed env-file path.
  - Source evidence: `.github/workflows/ci-security.yml`, `release-gate.ps1`, and `test-release-evidence-validators.ps1`.
  - Causal chain: release-gate reproducibility was hardened first, but the CI smoke command still carried older job-level environment assumptions.
  - Fix: run CI Compose config with the reviewed env-file path and add self-test coverage that fails if CI or release-gate Compose checks drift back to ambient discovery.
  - Validation: release evidence self-test, Compose safety self-test, Compose safety validation, explicit Compose config, and scoped diff check passed locally.
  - Prevention: CI smoke gates should use the same reviewed local inputs as release-gate validators.
- 2026-05-22: Suppression history computed total by loading all rows.
  - Symptom: `/api/v1/deliverability/suppressions/history` already counted complaint, hard-bounce, and unsubscribe rows with scoped count queries, but computed `total` by loading all tenant/workspace suppression rows and calling `.size()`.
  - Source evidence: `SuppressionController.java`, `SuppressionListRepository.java`, and `SuppressionControllerTest.java`.
  - Causal chain: the endpoint started as a small summary but retained an older list-based total even after reason-specific count queries were available.
  - Fix: add and use `countByTenantIdAndWorkspaceId` for total, preserving response shape and fail-closed tenant/workspace context.
  - Validation: focused suppression controller tests and full deliverability-service gate passed locally.
  - Prevention: summary endpoints should use count/aggregate queries rather than materializing rows for totals.
- 2026-05-22: Template library preferences used global browser-storage keys.
  - Symptom: favorites and recents could bleed across workspaces because template library state used unscoped local browser-storage keys.
  - Source evidence: `frontend/src/app/(workspace)/email/templates/page.tsx` and `frontend/tests/e2e/template-builder.spec.ts`.
  - Causal chain: the template library preference UI was added as a client-side convenience without carrying the active tenant/workspace context into the persistence key.
  - Fix: scope favorites and recents keys by active tenant/workspace, clear state when context is missing, ignore global legacy keys, and treat corrupt scoped JSON as empty.
  - Validation: targeted template-builder Playwright, frontend lint, frontend production build, and scoped diff check passed locally.
  - Prevention: any workspace UI preference persisted in browser storage must include the active routing context or fail closed when context is unavailable.
- 2026-05-22: Provider-capacity list reads preserved scoped filtering but not bounded reads.
  - Symptom: the delivery provider-capacity endpoint returned every tenant/workspace profile, which can grow across provider/domain/ISP rules.
  - Source evidence: `DeliveryOperationsController.java`, `ProviderCapacityService.java`, `ProviderCapacityProfileRepository.java`, and `ProviderCapacityServiceTest.java`.
  - Causal chain: provider capacity originally exposed a list response shape for operators and kept tenant/workspace scope, but the repository method never accepted a page bound.
  - Fix: add an optional controller limit, clamp to default/max values in the service, and pass a first-page `PageRequest` to the scoped repository query.
  - Validation: focused provider-capacity/RBAC tests, full delivery-service gate, and scoped diff check passed locally.
  - Prevention: operator list endpoints should preserve scope and response contracts while still enforcing default/max repository bounds.
- 2026-05-22: Audience async import processing reloaded jobs by raw ID after scoped creation.
  - Symptom: import creation/start was tenant/workspace scoped, but async processing and transactional progress/completion/failure paths reloaded by raw job ID.
  - Source evidence: `ImportProcessingService.java`, `ImportService.java`, and import service tests.
  - Causal chain: the async worker originally inferred tenant/workspace from the first raw job load, leaving later reloads and cancellation checks on the older ID-only path.
  - Fix: pass trusted tenant/workspace scope into async processing and use scoped job lookups before storage, save, event, or context side effects.
  - Validation: focused import tests and integrated backend gate passed locally.
  - Prevention: async workers should receive trusted scope from the caller and never infer scope from a raw ID lookup.
- 2026-05-22: Reputation recovery used an all-row scheduled scan.
  - Symptom: scheduled deliverability reputation recovery loaded every domain reputation row before applying recovery checks.
  - Source evidence: `ReputationEngine.java`, `DomainReputationRepository.java`, and `ReputationEngineTest.java`.
  - Causal chain: the hourly recovery job was written for simple iteration and never moved to bounded paging as scoped domain reputation rows grew.
  - Fix: read deterministic fixed-size pages and apply the existing recovery logic per page.
  - Validation: focused reputation engine tests and integrated backend gate passed locally.
  - Prevention: scheduled maintenance jobs must use bounded reads even when per-row behavior is unchanged.
- 2026-05-22: Operations assistance persisted broad telemetry maps.
  - Symptom: deterministic operations assistance accepted and persisted arbitrary telemetry JSON after normal request handling.
  - Source evidence: `OperationsAssistanceService.java` and `OperationsAssistancePerformanceServiceTest.java`.
  - Causal chain: governance docs existed, but this local assistance surface had not enforced workspace context plus telemetry minimization before persistence.
  - Fix: require workspace context, reject restricted telemetry-key shapes recursively, and persist only allowlisted operational fields.
  - Validation: focused foundation tests and integrated backend gate passed locally.
  - Prevention: deterministic assistance surfaces should apply data minimization before persistence, even before any external provider integration exists.
- 2026-05-22: Observability handoff validation stopped at alert runbook links.
  - Symptom: local validation checked alert runbook URLs but did not prove alert ownership, routing, receiver resolution, or dashboard panel references.
  - Source evidence: `validate-production-overlay.ps1`, `prometheus-alerts.yml`, `alertmanager.yml`, `grafana-legent-overview.json`, and the production hardening runbook.
  - Causal chain: earlier observability hardening added metrics and alerts, while local handoff validation did not yet cover the route/dashboard/runbook chain operators need.
  - Fix: validate team labels, Alertmanager routes and receivers, runbook anchors, Grafana UID/panel references, and receiver settings locally.
  - Validation: production overlay validation, release evidence self-test, local-only release gate, and scoped diff check passed locally.
  - Prevention: alert rules should ship with locally validated ownership, routing, dashboard, and runbook references before target firing evidence is collected.
- 2026-05-22: Data-extension controller pagination accepted unsafe page and size params.
  - Symptom: data-extension list and record reads could receive negative page values or excessive sizes from controller request params.
  - Source evidence: `DataExtensionController.java` and `DataExtensionControllerTest.java`.
  - Causal chain: service-level scoping was preserved, but controller param normalization lagged behind newer bounded-read conventions.
  - Fix: clamp negative pages to the first page, invalid sizes to the default, and excessive sizes to the shared maximum.
  - Validation: focused controller tests, integrated audience+content backend gate, and scoped diff check passed locally.
  - Prevention: controller read endpoints should normalize page and size before constructing service requests.
- 2026-05-22: Scheduled segment recompute scanned candidate rows without a page boundary.
  - Symptom: scheduled segment recompute could iterate all due segments in one repository read.
  - Source evidence: `SegmentEvaluationService.java`, `SegmentRepository.java`, and `SegmentEvaluationServiceTest.java`.
  - Causal chain: manual segment evaluation behavior had focused correctness coverage, while the scheduled maintenance path retained the simpler all-candidate scan.
  - Fix: scan due segment candidates in deterministic bounded pages and route each row through existing recompute behavior with context handling.
  - Validation: focused segment tests, integrated audience+content backend gate, and scoped diff check passed locally.
  - Prevention: scheduled maintenance scans should use deterministic page boundaries even when per-row business behavior is unchanged.
- 2026-05-22: Send-governance policy listing trusted raw page and size params.
  - Symptom: send-governance policy list reads could accept invalid or excessive pagination params at the controller boundary.
  - Source evidence: `SendGovernancePolicyController.java` and `SendGovernancePolicyControllerTest.java`.
  - Causal chain: the endpoint already kept scope and method security, but request-param bounds were not centralized at the controller boundary.
  - Fix: clamp page and size params to safe defaults and maximums before repository-facing list requests.
  - Validation: focused controller tests, integrated audience+content backend gate, and scoped diff check passed locally.
  - Prevention: operator list endpoints should apply page/size guards at the public controller boundary while keeping service-level scope authoritative.
- 2026-05-22: Startup tenant bootstrap loaded all tenants before queuing missing bootstrap work.
  - Symptom: `TenantBootstrapInitializer` used an all-row tenant read during application-ready startup.
  - Source evidence: `TenantBootstrapInitializer.java`, `TenantRepository.java`, and `TenantBootstrapInitializerTest.java`.
  - Causal chain: bootstrap initialization was simple and correctness-focused, while tenant-count growth and active/deleted filtering were handled elsewhere through paged repository patterns.
  - Fix: scan active non-deleted tenants in deterministic bounded pages and preserve the existing bootstrap-status skip plus request payload.
  - Validation: focused bootstrap tests, integrated foundation+content+platform backend gate, and scoped diff check passed locally.
  - Prevention: startup initializers should use bounded repository scans and active/deleted filters before queuing side effects.
- 2026-05-22: Platform global search returned all matching scoped rows.
  - Symptom: workspace-scoped search used a list repository method without a result bound.
  - Source evidence: `GlobalSearchService.java`, `SearchIndexDocRepository.java`, and `GlobalSearchServiceTest.java`.
  - Causal chain: the service preserved tenant/workspace filtering and a list response shape, but the repository signature predated bounded-read conventions.
  - Fix: use a bounded first-page repository query and return page content as the existing list shape.
  - Validation: focused search tests, integrated foundation+content+platform backend gate, and scoped diff check passed locally.
  - Prevention: search endpoints should preserve scope and response shape while applying explicit default/max result bounds.
- 2026-05-22: Unread notification reads were scoped but unbounded.
  - Symptom: unread notification lookup filtered by tenant/workspace/user but could return all unread rows.
  - Source evidence: `NotificationEngine.java`, `NotificationRepository.java`, and `NotificationEngineTest.java`.
  - Causal chain: ownership validation was implemented first, while result-count bounds were left to the list response path.
  - Fix: read the newest unread notifications through a bounded first-page request while keeping create and mark-read behavior unchanged.
  - Validation: focused notification tests, integrated foundation+content+platform backend gate, and scoped diff check passed locally.
  - Prevention: scoped user-facing list reads still need explicit bounded repository access.
- 2026-05-22: Template list/search controls had partial bounds.
  - Symptom: template list clamped only oversize values and template search returned all matching names.
  - Source evidence: `TemplateController.java`, `TemplateService.java`, `EmailTemplateRepository.java`, `TemplateServiceTest.java`, and `TemplateControllerTest.java`.
  - Causal chain: template list/search behavior preserved tenant/workspace scope and response contracts, but invalid page/size and search-result limits were not consistently normalized.
  - Fix: clamp list page/size params and bound template search with a first-page repository query.
  - Validation: focused content tests, integrated foundation+content+platform backend gate, and scoped diff check passed locally.
  - Prevention: list and search endpoints should normalize page/size inputs and apply search bounds even when returning a simple list.
- 2026-05-22: Provider health monitoring used unbounded active-provider and history reads.
  - Symptom: scheduled provider health monitoring could load every active provider and every recent provider health check before computing consecutive failures.
  - Source evidence: `ProviderHealthMonitoringService.java`, `SmtpProviderRepository.java`, `ProviderHealthCheckRepository.java`, and `ProviderHealthMonitoringServiceTest.java`.
  - Causal chain: health monitoring preserved workspace ownership but retained simple list repository methods from the initial operator implementation.
  - Fix: use deterministic ID-page active-provider scans and bounded newest-first recent history reads.
  - Validation: focused provider health tests and the integrated backend service gate passed locally.
  - Prevention: scheduled provider maintenance paths need bounded scans and bounded per-provider history windows.
- 2026-05-22: Campaign experiment winner evaluation scanned all active experiments.
  - Symptom: scheduled experiment evaluation could materialize every active experiment before winner checks.
  - Source evidence: `CampaignEngineService.java`, `CampaignExperimentRepository.java`, and `CampaignEngineServiceTest.java`.
  - Causal chain: winner-promotion correctness existed, but the scheduler had not been moved to bounded repository access.
  - Fix: scan active experiments in deterministic bounded pages while preserving winner-selection behavior.
  - Validation: focused campaign engine tests and the integrated backend service gate passed locally.
  - Prevention: scheduled campaign maintenance work should use page/cursor boundaries even when business logic remains unchanged.
- 2026-05-22: Suppression list endpoints exposed scoped but unbounded list reads.
  - Symptom: public and internal suppression list paths could return every tenant/workspace suppression row.
  - Source evidence: `SuppressionController.java`, `SuppressionListRepository.java`, `SuppressionControllerTest.java`, and `DomainControllerRbacTest.java`.
  - Causal chain: suppression safety focused on scope, bulk check, and history behavior before operator list bounds were added.
  - Fix: add optional bounded list limits, clamp defaults/max values, and align internal guard reflection coverage with the bounded signature.
  - Validation: focused suppression/RBAC tests and the integrated backend service gate passed locally.
  - Prevention: deliverability list endpoints should always pair scope with explicit default/max result bounds.
- 2026-05-22: Tracking campaign summary list was scoped but unbounded.
  - Symptom: analytics campaign summary listing could return every scoped summary row.
  - Source evidence: `AnalyticsController.java`, `CampaignSummaryRepository.java`, and `AnalyticsControllerTest.java`.
  - Causal chain: tenant/workspace filters were in place, but list response compatibility hid the lack of repository-level bounds.
  - Fix: read a bounded first page through the scoped repository and preserve the existing list response shape.
  - Validation: focused analytics controller tests and the integrated backend service gate passed locally.
  - Prevention: analytics list endpoints should expose explicit result bounds even for existing list-shaped contracts.
- 2026-05-22: Warmup and rate-state operator endpoints listed all scoped state rows.
  - Symptom: delivery operator warmup and rate-state endpoints used scoped repository reads without a row limit.
  - Source evidence: `DeliveryOperationsController.java`, `WarmupService.java`, `SendRateControlService.java`, warmup/rate repositories, and delivery tests.
  - Causal chain: hot-path reservations were implemented separately from operator read paths, leaving state-list views with the older all-row access pattern.
  - Fix: add optional limit params and clamp service-level first-page reads to default/max bounds.
  - Validation: focused warmup/rate tests and the integrated backend service gate passed locally.
  - Prevention: operator state endpoints should use bounded first pages and only add deeper navigation as an explicit API contract.
- 2026-05-22: Admin settings list scanned all config rows in memory.
  - Symptom: foundation admin settings listing used `findAll()` and filtered tenant/context visibility in service memory.
  - Source evidence: `AdminSettingsService.java`, `ConfigRepository.java`, and `AdminSettingsServiceTest.java`.
  - Causal chain: visibility logic lived in service code and was never pushed into a scoped pageable repository query.
  - Fix: add a scoped visible-settings repository query and page through results in bounded chunks.
  - Validation: focused admin settings tests and the integrated backend service gate passed locally.
  - Prevention: admin configuration listings should push tenant/context filters into repository queries and page the result set.
- 2026-05-22: Campaign batch execution still had a tenant-only fallback.
  - Symptom: direct batch execution without workspace context could fall back to `findById()` plus tenant filtering.
  - Source evidence: `SendExecutionService.java`, `SendExecutionServiceTest.java`, and `CampaignEventConsumerTest.java`.
  - Causal chain: retry support retained a legacy tenant-only lookup after batch ownership moved to tenant/workspace scope.
  - Fix: require workspace context before batch lookup and keep event consumer coverage for tenant/workspace context setup.
  - Validation: focused campaign tests and the integrated backend reactor gate passed locally.
  - Prevention: send execution entry points should fail closed before repository access when workspace context is missing.
- 2026-05-22: Conversion ingestion lacked method-level write authorization.
  - Symptom: `POST /api/v1/tracking/events` depended on route/security configuration without a controller method guard.
  - Source evidence: `IngestionController.java` and tracking controller RBAC tests.
  - Causal chain: public signed open/click endpoints and authenticated conversion ingestion lived in the same controller, but only the public paths had explicit signed-route behavior documented in tests.
  - Fix: require tracking or analytics write permission on conversion ingestion and keep open/click methods public.
  - Validation: focused tracking tests and the integrated backend reactor gate passed locally.
  - Prevention: mixed public/protected controllers need method-level authorization coverage for every protected write.
- 2026-05-22: Backend sanitizer preserved blank-target links without safe rel normalization.
  - Symptom: sanitized anchors could retain `target="_blank"` without guaranteed `noopener noreferrer`.
  - Source evidence: `EmailContentValidationService.java` and `EmailContentValidationServiceTest.java`.
  - Causal chain: allowed-attribute filtering covered `target`, but rel normalization was not paired with new browsing-context behavior.
  - Fix: add safe rel values for blank-target anchors while preserving approved rel tokens and dropping unsafe tokens.
  - Validation: focused content sanitizer tests and the integrated backend reactor gate passed locally.
  - Prevention: sanitizer changes should couple target/window behavior with rel and URL policy tests.
- 2026-05-22: Automation and webhook list APIs preserved list contracts without repository bounds.
  - Symptom: workflow, schedule, activity, and webhook admin listing paths used scoped but unbounded list repository methods.
  - Source evidence: automation services/repositories/tests and `WebhookController`/`WebhookConfigRepository` tests.
  - Causal chain: tenant/workspace scoping existed, but list response compatibility hid missing first-page bounds.
  - Fix: move list APIs to scoped first-page repository access; automation cycle validation now follows scoped dependency IDs instead of scanning all activities.
  - Validation: focused automation/platform tests and the integrated backend reactor gate passed locally.
  - Prevention: list-shaped compatibility endpoints still require explicit default/max repository bounds.
- 2026-05-22: Compose safety validation missed high-risk runtime settings.
  - Symptom: local Compose validator covered image/config hygiene but not privileged runtime, host namespaces, Docker control socket binds, or broad capability additions.
  - Source evidence: `validate-compose-safety.ps1` and `test-release-evidence-validators.ps1`.
  - Causal chain: earlier release validation focused on evidence and image/config inputs before runtime hardening checks were added.
  - Fix: reject the high-risk settings and run the Compose safety self-test from release evidence validator self-tests.
  - Validation: Compose safety self-test, release evidence validator self-test, and scoped diff check passed locally.
  - Prevention: release validators should include negative fixtures for unsafe local runtime capabilities.
- 2026-05-22: Frontend context bootstrap ignored preferred environment ID.
  - Symptom: auth context selection considered tenant/workspace preference but not preferred environment when multiple workspace contexts existed.
  - Source evidence: `context-bootstrap.ts` and `context-bootstrap.spec.ts`.
  - Causal chain: environment context persisted after workspace context was added, but bootstrap matching logic was not upgraded to use it as a tie breaker.
  - Fix: match preferred environment inside tenant/workspace candidates with deterministic fallback.
  - Validation: frontend lint, production build, and context Playwright passed locally.
  - Prevention: context bootstrap tests should cover tenant, workspace, and environment together.
- 2026-05-22: DMARC and template approval history were scoped but unbounded.
  - Symptom: DMARC report history and content template approval history could return all scoped rows.
  - Source evidence: `DmarcController.java`, `DmarcReportRepository.java`, `TemplateWorkflowService.java`, `TemplateApprovalRepository.java`, and focused tests.
  - Causal chain: tenant/workspace/RBAC behavior existed before row-count bounds were added to these operator history surfaces.
  - Fix: use bounded first-page repository reads with default and max limits while preserving list responses.
  - Validation: focused worker tests and the integrated backend reactor gate passed locally.
  - Prevention: history/list endpoints need explicit default and max bounds even when preserving legacy list response shapes.
- 2026-05-22: Feature flag admin paths and notification routes had implicit context/auth assumptions.
  - Symptom: feature flag admin list/create could reach service code without trusted tenant context, and notification endpoints lacked explicit method authorization.
  - Source evidence: `FeatureFlagController.java`, `NotificationController.java`, and controller/service tests.
  - Causal chain: filters and service scoping protected normal paths, but controller-level fail-closed and method-auth coverage lagged behind stricter tenant/auth conventions.
  - Fix: require tenant context before feature flag admin list/create and add authenticated method guards to notification read/mark-read.
  - Validation: focused worker tests and the integrated backend reactor gate passed locally.
  - Prevention: protected controller methods should declare auth/context requirements close to the endpoint and test missing-context behavior.
- 2026-05-22: Route-map negative fixtures did not cover tracking policy drift broadly enough.
  - Symptom: route-map validation could pass live config but fixture coverage did not explicitly test several tracking route or ingress-policy regressions.
  - Source evidence: `test-route-map-validator.ps1` and `validate-route-map.ps1`.
  - Causal chain: prior validator work covered main route drift first, leaving additional policy-specific regressions to future fixture expansion.
  - Fix: add tracking-focused negative fixtures and emit accumulated validation errors before exit.
  - Validation: route-map fixture harness and live route validation passed locally.
  - Prevention: validators should keep negative fixtures for route aliases, edge policy, and ingress prefix coverage.
- 2026-05-22: Login could leave stale client context after backend auth succeeded but workspace bootstrap failed.
  - Symptom: local user, role, tenant, workspace, or environment metadata could survive a post-login context bootstrap failure.
  - Source evidence: `PublicAuthViews.tsx` and `auth.spec.ts`.
  - Causal chain: UI metadata was written before active workspace context was confirmed.
  - Fix: clear local auth/context state before bootstrap, write session metadata only after valid workspace context, and use the same cleanup on bootstrap failure.
  - Validation: frontend lint, production build, and auth Playwright passed locally.
  - Prevention: public auth flows should treat workspace bootstrap as part of the local-auth success boundary.
- 2026-05-22: Analytics and version/history/admin list surfaces preserved list contracts without explicit bounds.
  - Symptom: tracking analytics rollups/timelines, content template versions, and identity user/invitation admin lists could read unbounded scoped result sets.
  - Source evidence: `AnalyticsService.java`, `TemplateVersionService.java`, `UserService.java`, `AuthService.java`, and focused tests.
  - Causal chain: tenant/workspace scoping was implemented before default/max result bounds were consistently applied to legacy list-shaped APIs.
  - Fix: add bounded windows, bucket caps, first-page reads, and optional limit parameters while preserving existing response shapes.
  - Validation: focused worker tests and integrated backend reactor gate passed locally.
  - Prevention: every list-shaped compatibility endpoint needs a default/max bound even when pagination metadata is not yet added.
- 2026-05-22: Config version controller read/mutation paths trusted ambient tenant context too late.
  - Symptom: config version history, compare, and rollback could call service methods with missing tenant context.
  - Source evidence: `ConfigVersionController.java` and `ConfigVersionControllerTest.java`.
  - Causal chain: scoped service logic existed, but controller entry points did not fail closed before service invocation.
  - Fix: require tenant context at controller entry for all config version endpoints and prove no service interaction when missing.
  - Validation: focused foundation tests and integrated backend reactor gate passed locally.
  - Prevention: protected controller paths should enforce required context before any service call.
- 2026-05-22: CI did not execute new local route and Compose safety harnesses.
  - Symptom: local validator hardening existed but was not wired into the GitHub Actions compose smoke lane.
  - Source evidence: `.github/workflows/ci-security.yml`, `test-route-map-validator.ps1`, and `validate-compose-safety.ps1`.
  - Causal chain: validator scripts were improved locally before CI orchestration was updated to run them.
  - Fix: add CI steps for route-map fixture validation and Compose safety self-test/validation using portable PowerShell child execution.
  - Validation: local route-map fixture/live validation and Compose safety gates passed.
  - Prevention: every new release/safety validator should be wired into CI or explicitly documented as local-only.
- 2026-05-22: Compliance evidence treated missing workspace context as broad scope.
  - Symptom: compliance evidence surfaces could reach repository reads or writes without a trusted workspace in context.
  - Source evidence: `ComplianceEvidenceService.java`, `ComplianceController.java`, and `ComplianceEvidenceServiceTest.java`.
  - Causal chain: earlier compliance hardening focused on tenant and privacy-request mutation paths while list/export/create paths still allowed nullable workspace behavior.
  - Fix: require current workspace context, reject explicit workspace mismatch, and use exact workspace predicates before repository access.
  - Validation: focused worker tests and integrated foundation/tracking/deliverability/content backend gate passed locally.
  - Prevention: compliance surfaces should require tenant and workspace context at entry unless a separate tenant-global policy is designed.
- 2026-05-22: Tracking WebSocket handshake accepted request headers as scope fallback.
  - Symptom: authenticated WebSocket handshakes without principal workspace context could derive tenant/workspace from upgrade headers.
  - Source evidence: `TenantHandshakeInterceptor.java` and `TenantHandshakeInterceptorTest.java`.
  - Causal chain: the interceptor supported multiple transport-context sources, but trusted authenticated scope and request metadata were not clearly separated.
  - Fix: remove header fallback and keep scope resolution to authenticated context or signed query scope.
  - Validation: focused worker tests and integrated backend gate passed locally.
  - Prevention: upgrade request headers must not become tenant/workspace authority for protected channels.
- 2026-05-22: Reputation and content-block version reads preserved list contracts without bounds.
  - Symptom: deliverability reputation/insight reads and content block version listing/publish cleanup could materialize larger scoped result sets than needed.
  - Source evidence: deliverability reputation controller/service/repository tests and content block service/repository tests.
  - Causal chain: tenant/workspace scope existed, but legacy list-shaped APIs predated consistent default/max repository bounds.
  - Fix: add bounded first-page queries, default/max limit normalization, and scoped bulk cleanup where safe.
  - Validation: focused worker tests and integrated backend gate passed locally.
  - Prevention: compatibility list APIs still need explicit repository-level bounds.
- 2026-05-22: Frontend public API helpers did not share the core origin guard.
  - Symptom: public helper calls could be constructed with external absolute URLs before adapter dispatch.
  - Source evidence: `api-client.ts` and `api-client-context.spec.ts`.
  - Causal chain: credentialed API calls had stricter preflight than unauthenticated public helpers.
  - Fix: route public helper targets through the same origin guard while keeping public calls credentialless and header-free.
  - Validation: frontend lint, production build, and API-client Playwright passed locally.
  - Prevention: all frontend request helpers should guard origin before adapter dispatch, regardless of auth mode.
- 2026-05-22: Release validators assumed Windows PowerShell child execution and missed CI coverage for compose health parser behavior.
  - Symptom: local release utility child processes used a hardcoded executable name, and CI did not exercise compose health self-test logic.
  - Source evidence: `release-gate.ps1`, `test-release-evidence-validators.ps1`, `validate-compose-health.ps1`, and `.github/workflows/ci-security.yml`.
  - Causal chain: the validation scripts were Windows-first and parser self-tests were local-only.
  - Fix: reuse the active PowerShell executable where possible, improve child-process capture, add health parser self-tests, and wire them into CI.
  - Validation: release evidence validator self-test, local release gate, compose health/safety validators, and route validators passed locally.
  - Prevention: ops validators should self-test parser behavior and avoid hardcoded shell assumptions.
