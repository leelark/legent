# Bug History

Fresh baseline date: 2026-05-20.

## 2026-05-20 Suppression Delete Tenant Scope

Source: read-only predictive segment/audience audit, `services/audience-service/src/main/java/com/legent/audience/service/SuppressionService.java`, `SuppressionRepository.java`, `SuppressionServiceTest.java`.

Status: fixed.

Impact: `SuppressionService.delete` previously used raw `findById(id)`, so a caller with `audience:delete` and a known suppression ID could soft-delete a suppression outside the current tenant/workspace.

Resolution: delete now uses tenant+workspace+ID+not-deleted lookup before soft delete. Focused tests cover same-scope delete and foreign-ID denial.

## 2026-05-20 Segment Rules Fail Closed

Source: read-only predictive segment/audience audit, `services/audience-service/src/main/java/com/legent/audience/service/SegmentEvaluationService.java`, `SegmentService.java`, `SegmentEvaluationServiceTest.java`, `SegmentServiceTest.java`.

Status: fixed.

Impact: `list_membership` conditions previously mapped to `null` before `IN_LIST` and `NOT_IN_LIST` handling, so list membership rules were silently skipped. Unsupported operators could also return `null` and be skipped, broadening segments.

Resolution: evaluator now handles list membership operators before field-column mapping and throws on unsupported operators or invalid list/operator combinations. Segment create/update validates rule trees before persistence. Focused and full audience tests passed.

## 2026-05-20 Automation Studio Live Run Confirmation

Source: read-only automation audit, `services/automation-service/src/main/java/com/legent/automation/service/AutomationStudioService.java`, `AutomationStudioDto.java`, `AutomationStudioServiceTest.java`.

Status: fixed.

Impact: an empty JSON run request `{}` deserialized to `dryRun=false` because `RunRequest.dryRun` was primitive boolean, allowing ACTIVE SQL/IMPORT activities to execute live without an explicit live-run confirmation.

Resolution: `RunRequest.dryRun` is nullable and defaults to dry-run unless explicitly false, live runs require `confirmLiveRun=true`, and focused tests cover empty request default dry-run, missing confirmation denial, confirmed live runs, and unsupported legacy active rows.

## 2026-05-20 Platform Event Idempotency

Source: read-only platform/Kafka audit, `services/platform-service/src/main/java/com/legent/platform/event/PlatformEventConsumer.java`, `PlatformEventIdempotencyService.java`, `V7__platform_event_idempotency.sql`, `PlatformEventConsumerTest.java`, `PlatformEventIdempotencyServiceTest.java`.

Status: fixed locally.

Impact: platform Kafka replays could duplicate webhook dispatches, notifications, and search indexing because event IDs and idempotency keys were validated but not claimed before side effects.

Resolution: platform consumers now claim tenant/workspace-scoped event identity before side effects, skip duplicates, release pending claims only when side effects fail before completion, and keep claims when processed-marker updates fail after a side effect to prevent duplicate replay. Focused and full platform tests passed.

## 2026-05-20 Audience Resolution Final Eligibility First Slice

Source: campaign final-gate audit, `services/audience-service/src/main/java/com/legent/audience/event/AudienceResolutionConsumer.java`, `SendEligibilityService.java`, `SuppressionRepository.java`, `AudienceResolutionConsumerTest.java`, `SendEligibilityServiceTest.java`.

Status: fixed locally; parent work item remains `REVIEW`.

Impact: audience resolution previously filtered external deliverability suppressions and then used an in-memory `isSendEligible` shortcut, which skipped local audience suppressions and did not enforce nested `channels.email=false` or future `pausedUntil` preferences before campaign batching.

Resolution: audience resolution now calls authoritative batch eligibility before publishing resolved chunks; `SendEligibilityService` checks local suppressions in bulk, nested email-channel denial, and pause windows; the shortcut was removed. Remaining review item: campaign legacy recipient payloads need an eligibility marker contract before the parent can be marked done.

## 2026-05-25 Full QA Sweep Runtime/Test Blockers

Source: full local validation from clean build artifacts and fresh Docker image build, including `.\mvnw.cmd test`, `.\mvnw.cmd "-Dtest=NoSuchTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-DskipITs=false" verify`, frontend lint/build/Vitest/Playwright suites, route/release validators, `docker compose --env-file .env.example build --no-cache`, `docker compose --env-file .env.example up -d --build`, `scripts/ops/validate-compose-health.ps1`, and service logs.

Status: fixed locally after logging per QA-first instruction.

Impact and reproduction:

- `qa-20260525-campaign-delayed-publish-unit-failure`: full Maven tests fail in `SendExecutionServiceTest.waitsForDelayedPublishBeforeCompletingBatch`; expected publish to `email.send.requested` is never called.
- `qa-20260525-clickhouse-nullable-sort-key`: local ClickHouse exits during init because `infrastructure/docker/local/clickhouse-init/init.sql` creates `raw_events` with nullable columns in the MergeTree `ORDER BY` while nullable sort keys are disabled.
- `qa-20260525-compose-placeholder-runtime-secrets`: runtime startup with `.env.example` fails closed for services that require non-placeholder internal service credentials. Campaign, content, and deliverability reject `LEGENT_INTERNAL_API_TOKEN`; delivery rejects the placeholder/non-Base64 encryption salt.
- `qa-20260525-identity-active-query-startup`: identity-service fails startup because Spring Data cannot resolve repository method property `active` against the `User` entity boolean field `isActive`.
- `qa-20260525-audience-char-varchar-schema-validation`: audience-service fails Hibernate schema validation because `contact_lifecycle_audit.email_sha256` is `CHAR(64)` in PostgreSQL while the entity expects `varchar(64)`. Follow-up Compose evidence showed changing only the entity `columnDefinition` is insufficient because Hibernate still validates the Java `String` as JDBC `VARCHAR`; fix must move the schema forward with a new migration.
- `qa-20260525-foundation-audit-event-length`: foundation-service starts but Kafka tenant bootstrap handling logs `DataIntegrityViolationException: value too long for type character varying(26)` while inserting `core_audit_events`.
- `qa-20260525-compose-health-validator-blind-spot`: `scripts/ops/validate-compose-health.ps1` reports success even when Compose has exited/created services and starting health states because it only inspects default `docker compose ps` output and treats `starting` as acceptable.
- `qa-20260525-compose-health-validator-completed-job-false-negative`: after tightening Compose state checks, the validator correctly fails unhealthy services but also fails expected one-shot setup jobs (`postgres-init`, `kafka-setup`) that exit `0` and are depended on via `service_completed_successfully`.
- `qa-20260525-testcontainers-docker-skip`: backend Failsafe verify passes, but `TrackingEventIdempotencyServiceIT` skips seven Testcontainers-backed tests because the Java Docker client cannot discover the local Docker environment over Docker Desktop's Windows pipe.
- `qa-20260525-frontend-target-login-smoke-skipped`: full Chromium Playwright passes locally, but target login smoke remains skipped without `E2E_ADMIN_EMAIL` and `E2E_ADMIN_PASSWORD`.
- `qa-20260525-frontend-coverage-low`: frontend coverage gate passes conservative thresholds, but observed coverage remains very low at roughly 1.99% statements, 1.04% branches, 0.24% functions, and 2.51% lines.
- `qa-20260525-delivery-rate-reclaim-test-failure`: after initial fixes, full Maven tests still fail in `SendRateControlServiceTest.reserve_CapacityPressureReclaimsBoundedOldestExpiredReservationsOnce`; the rate reservation reclaim path reports one used token where the test contract expects two after reclaiming expired leases and reserving a fresh send.
- `qa-20260525-delivery-open-capacity-expired-reservation-test-failure`: final full Maven rerun still fails in `SendRateControlServiceTest.reserve_OpenCapacityDoesNotSweepExpiredReservations`; the open-capacity path reports one used token where the test contract expects two, indicating expired reservation setup is being reclaimed or not counted inconsistently before the pressure path should run.
- `qa-20260525-delivery-h2-jsonb-ddl-warning`: delivery and deliverability JPA slice tests emit H2 DDL warnings for PostgreSQL `jsonb` column definitions while using `ddl-auto=create-drop`; current observed hard failure is the rate reclaim assertion, but these warnings keep local test evidence noisy and can mask future repository boot failures.
- `qa-20260525-runtime-metrics-service-constructor-injection`: fresh Compose startup fails delivery and campaign because `DeliveryRetryMetricsService`, `CampaignDeadLetterMetricsService`, and `CampaignRetryMetricsService` each have a public production constructor plus a package-private test constructor, but the production constructors are not annotated for Spring injection; Spring attempts default construction and fails with `No default constructor found`.
- `qa-20260525-public-missing-route-500`: API/SIT gateway check shows `GET /api/v1/public` returns 500. Foundation logs show Spring raises `NoResourceFoundException` for an unmapped static/API resource, and the shared `GlobalExceptionHandler` maps it through the generic 500 path instead of returning a 404 response.
- `qa-20260525-foundation-bootstrap-status-duplicate`: final fresh Compose log scan after full no-cache rebuild shows foundation-service logs a startup `ConstraintViolationException`/PostgreSQL duplicate-key error inserting `tenant_bootstrap_status` for default tenant `01HTENANT000000000000000001`. The stack remains healthy, but bootstrap is not idempotent under concurrent/default provisioning paths and violates the no-runtime-error release expectation.

Owner: QA/Test Architect for evidence, then module owners for campaign, runtime/platform, identity, audience, foundation, and DevOps fixes.

Resolution and validation: fixed the campaign async test, ClickHouse local init DDL, identity active-user query method, audience/foundation schema drift via forward migrations, Compose health validator state handling, Testcontainers Docker discovery dependency level, H2 JSONB test noise, campaign/delivery metric service constructor injection, shared missing-route 404 mapping, foundation bootstrap event ordering, delivery rate-control window/reclaim timing, and the local Compose placeholder recovery path through `scripts/ops/start-local-compose.ps1`. Regression coverage was added or tightened in the affected modules. Full `.\mvnw.cmd test`, `.\mvnw.cmd -DskipTests install`, backend Failsafe verify with Testcontainers enabled, frontend lint/unit/coverage/build/Playwright suites, route/release/overlay validators, full no-cache Docker build plus targeted no-cache rebuilds after final service fixes, fresh Compose volume startup, API/SIT smoke checks, Compose health validation, and final startup log scan passed locally.

Residual risk: credentialed target login smoke remains skipped without `E2E_ADMIN_EMAIL` and `E2E_ADMIN_PASSWORD`, frontend coverage remains low despite passing conservative gates, and production promotion still requires target evidence for egress, image provenance, live smoke/load/restore, monitoring, TLS/admission, CI/security transcript, and provider-approved high-volume proof.

Next action: keep production readiness blocked pending target evidence and expand frontend/credentialed UAT coverage in follow-up work.

The fresh 2026-05-20 baseline initially had no open product bug entries; current confirmed bugs are recorded above.

When a bug is confirmed:
- Record date, source file or command, impact, reproduction, owner, fix status, validation, and residual risk.
- Move root-cause analysis to `root-cause-history.md`.
- Move validated fixes to `successful-fixes.md`.
