# Test Impact Map

Last updated: 2026-05-13.

Source: `rg --files services shared | rg 'src\\test'`, `rg --files frontend | rg 'tests|spec'`, `frontend/package.json`, `.github/workflows/ci-security.yml`.

- Backend/shared Java tests found by later consistency review: 58 service test files and 15 shared test files.
- Frontend Playwright specs found: 7 under `frontend/tests/e2e`; frontend scan counted 17 tests plus shared workspace mock support.
- Main backend gate: `.\mvnw.cmd test`.
- Service gate example: `.\mvnw.cmd -pl services/campaign-service -am test`.
- Frontend gates: `npm run lint`, `npm run build`, `npm run test:e2e:smoke`.
- Route/config gates: `scripts\ops\validate-route-map.ps1`, `docker compose config --quiet`, `kubectl kustomize infrastructure/kubernetes/overlays/production`.

Impact rules:

- Tenant logic needs missing-context and cross-tenant isolation tests.
- Kafka changes need consumer/publisher/idempotency tests.
- Frontend visible route changes need lint plus Playwright/browser verification when feasible.
- High-volume path changes need bounded performance validation or explicit residual risk.
- Audience chunking tests needed: large audience cursor boundaries, exclude-only audience, suppression-client paging, idempotent chunk IDs, Kafka payload-size guard. Target: `.\mvnw.cmd -pl services/audience-service,services/campaign-service -am test`.
- Audience chunking baseline covered 2026-05-13: `AudienceResolutionConsumerTest` verifies multi-chunk publish and empty-audience last-chunk semantics. Remaining needed: cursor/page boundaries, exclude-only large audiences, paged suppression checks, and Kafka payload-size guard. Target: `.\mvnw.cmd -pl services/audience-service,services/campaign-service -am test`.
- Kafka partitioning baseline covered 2026-05-13: `EventPublisherTest` verifies high-volume payload routing keys, no tenant fallback for high-volume topics, rejection without non-tenant metadata, custom tenant-key replacement, and low-volume tenant fallback. Remaining needed: service-level event contract tests for each high-throughput publisher.
- Kafka trusted package baseline covered 2026-05-13: shared config test asserts narrow allowlist and disabled type headers. Remaining needed: hostile type-header deserialization tests and service consumer contract tests.
- Consumer retry/DLQ tests needed: tracking, platform, deliverability, automation, and remaining failure paths must prove failures are not silently acknowledged. Delivery send-request propagation baseline covered 2026-05-13 by `DeliveryEventConsumerTest`. Target: `.\mvnw.cmd -pl services/tracking-service,services/platform-service,services/delivery-service,services/deliverability-service,services/automation-service -am test`.
- Tenant workspace conflict baseline covered 2026-05-13: `TenantFilterTest` rejects workspace/environment header conflicts against authenticated context and preserves authenticated workspace when the header is missing. Validation: `.\mvnw.cmd -pl shared/legent-security -am test`.
- Security config tests needed: no wildcard trusted packages, non-test DDL defaults validate, tracking WebSocket auth/workspace handshake.
- Frontend gaps: no component/unit tests found, limited API-client/header assertion coverage, real login smoke skipped unless `E2E_ADMIN_EMAIL` and `E2E_ADMIN_PASSWORD` are present. Workspace smoke covers four routes; visual smoke covers eight shell cases.
