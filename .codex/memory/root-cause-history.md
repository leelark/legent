# Root Cause History

Fresh baseline date: 2026-05-20.

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

Root-cause entries must include:
- symptom,
- source evidence,
- causal chain,
- fix,
- validation,
- prevention,
- related memory links.
