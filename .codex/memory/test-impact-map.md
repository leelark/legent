# Test Impact Map

Fresh baseline date: 2026-05-20.

Default test selection:
- Backend service behavior: focused Maven module tests with `-pl <module> -am test`.
- Shared module changes: shared module tests plus impacted service tests.
- Kafka/event behavior: publisher/consumer contract tests and retry/DLQ tests where available.
- Tenant/workspace behavior: missing-context and cross-tenant isolation tests.
- Frontend UI changes: lint, build, and impacted Playwright/browser checks.
- Route/runtime changes: route map validator, Compose config, Nginx/ingress render checks.
- Release changes: release gate and evidence validators.

If a gate cannot run, record the exact reason and residual risk.
