# TESTING_SKILL.md

Use this skill when adding, changing, or validating behavior in the Legent repository.

## Testing Philosophy

Every major change needs tests. Validate both success and failure scenarios. Validate performance-critical flows. Validate security-critical flows. Tests must protect tenant isolation, workspace context, campaign correctness, deliverability safety, and event-driven reliability.

## Test Types

### Unit Testing

Use for:

- Validation logic.
- Mapping logic.
- Policy decisions.
- Provider scoring.
- Warmup and rate calculations.
- Inbox safety checks.
- Segment rule parsing.
- Content sanitization and rendering helpers.
- Frontend pure utilities, hooks, stores, and component states.

Rules:

- Keep unit tests fast and deterministic.
- Cover edge cases and invalid input.
- Test denied behavior, not only happy path.

### Integration Testing

Use for:

- Spring controller/service/repository integration.
- Flyway migration compatibility.
- PostgreSQL queries.
- Kafka producer/consumer behavior.
- Tenant and workspace filters.
- Auth/session/cookie behavior.
- Import processing.
- Send job, batch, delivery, tracking, automation, and webhook flows.

Rules:

- Use Testcontainers where the repository pattern already supports it.
- Test transaction boundaries and idempotency.
- Test retry/DLQ behavior for Kafka listeners when changed.
- Test cross-service event contracts without requiring all services to boot unless the workflow requires it.

### End-To-End Testing

Use Playwright for:

- Public page smoke.
- Login/session surfaces.
- Workspace shell route loading.
- Campaign launch screens.
- Audience import screens.
- Template editor critical behavior.
- Analytics/admin/settings navigation.
- Mobile and desktop overflow checks.

Rules:

- Prefer stable selectors and semantic roles.
- Mock backend only when testing frontend rendering. Use real APIs for critical workflow E2E when feasible.
- Verify authenticated and unauthenticated behavior.

### Edge Case Testing

Required edge cases include:

- Missing tenant/workspace/environment headers.
- Tenant mismatch between JWT and request headers.
- Empty audience, duplicate subscribers, invalid emails, unsubscribed recipients, suppressions.
- New sender/domain warmup state.
- Provider unavailable or circuit breaker open.
- Missing template/content.
- Invalid personalization token.
- Large import file with malformed rows.
- Tracking link signature failure.
- SCIM token missing, expired, or missing scope.
- Webhook retry exhaustion.

### Load Testing

Use for:

- Audience import throughput.
- Segment recomputation.
- Campaign audience resolution.
- Batch creation.
- Render throughput.
- Email send request production/consumption.
- Delivery provider/rate/warmup path.
- Tracking ingestion and ClickHouse writes.
- Webhook retry processing.

Rules:

- Define target rate, duration, payload shape, tenant count, workspace count, provider/domain distribution, and acceptable error budget.
- Measure Kafka lag, DB CPU/locks/connections, Redis latency, provider throttle responses, consumer throughput, JVM memory/GC, p95/p99 latency, DLQ volume, and stuck jobs.
- For the stated 10 lakh in 10 hours target, test at and above 27.8 sends/second sustained only with warmed sender assumptions. Do not load-test by bypassing warmup, suppression, or provider safety.

### Failure Testing

Use for:

- Kafka broker restart.
- Consumer exception and retry.
- DLQ route.
- Provider timeout/throttle/failure.
- Database outage or connection exhaustion.
- Redis outage.
- MinIO outage during import.
- ClickHouse outage during tracking.
- Duplicate event delivery.
- Partial batch failure.
- Stuck send job recovery.

Rules:

- Failures must produce observable status, retry, DLQ, alert, or user-visible error.
- Never accept silent loss for campaign, delivery, tracking, import, or automation events.

### Security Testing

Use for:

- Auth cookies and refresh.
- CORS and origin/referer guard.
- Tenant/workspace isolation.
- Unauthorized route access.
- SCIM bearer token scopes.
- Internal API token endpoints.
- Content sanitization.
- Signed tracking URL verification.
- Outbound URL guard.
- Secret placeholder rejection.

Rules:

- Test both allowed and denied requests.
- Do not print real secrets.
- Use `.env.example` or test-specific fake values.

## Commands

Backend all tests:

```powershell
.\mvnw.cmd test
```

Backend service test:

```powershell
.\mvnw.cmd -pl services/campaign-service -am test
```

Frontend lint/build/E2E:

```powershell
cd frontend
npm run lint
npm run build
npm run test:e2e:smoke
```

Environment validation:

```powershell
.\scripts\ops\validate-env.ps1 -Path .\.env.example -AllowLocalDefaults
docker compose config
kubectl kustomize infrastructure/kubernetes/overlays/production
```

## Minimum Test Expectations By Change

- Frontend UI-only change: lint plus targeted Playwright or component-level coverage where available.
- Backend service logic change: service unit test plus repository/controller integration test if persistence/API behavior changed.
- Shared module change: tests in the shared module plus at least one affected service test.
- Kafka/event change: producer/consumer contract test and failure/idempotency test.
- Migration change: migration test or clean startup against test database.
- Security change: allowed and denied integration tests.
- Send pipeline change: campaign, audience, delivery, and tracking impact tests.
- Performance hot path change: targeted load or benchmark evidence.

## Test Quality Rules

- Tests must assert behavior, not implementation trivia.
- Avoid sleeps when polling or deterministic hooks are possible.
- Use generated IDs and isolated tenant/workspace context.
- Clean up or isolate state.
- Keep mocks realistic around event envelopes and API responses.
- Do not skip failing tests without documenting the defect and owner.
- Do not lower assertions just to make tests pass.
